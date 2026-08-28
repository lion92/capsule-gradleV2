import React from 'react';
import { AbsoluteFill, Sequence, OffthreadVideo, staticFile, useCurrentFrame, useVideoConfig, interpolate } from 'remotion';

/**
 * Composition d'une capsule pédagogique.
 *
 * Tout est piloté par le numéro d'image, jamais par une animation CSS : un
 * rendu image par image se déplace librement dans le temps, et une animation
 * fondée sur l'horloge ne se reproduit pas à l'identique quand il le fait.
 */

const EASE_OUT = (t) => 1 - Math.pow(1 - t, 3);

/** Durée de pose d'un bloc, en secondes. */
const REVEAL_SECS = 0.55;

/** Fondu enchaîné d'une diapo à la suivante, en images. */
const CROSSFADE_FRAMES = 20;

/**
 * Part du fondu franchie avant que le texte de la diapo entrante ne paraisse.
 *
 * Le schéma, lui, se dissout normalement : deux schémas n'occupent pas la même
 * place, et leur superposition brève passe pour un enchaînement.
 */
const CONTENT_GATE = 0.72;

/**
 * Bornes de l'ajustement d'un schéma à la durée de sa diapo.
 *
 * Mesurées sur les neuf clips de « Terre ronde » : les écarts réels tiennent
 * dans ±5 % sauf un, et une correction de cet ordre est invisible. Un facteur
 * franc, lui, se remarque — d'où des bornes serrées.
 */
const MAX_SPEEDUP = 1.25;
const MIN_SLOWDOWN = 0.8;

/**
 * Rythme de lecture : à quelle seconde de la diapo chaque bloc se pose.
 *
 * ## Pourquoi des secondes, et pas une fraction de la diapo
 *
 * Les blocs se posaient tous dans les 28 premières images — moins d'une
 * seconde — puis l'image ne bougeait plus. Sur la capsule « Terre ronde », la
 * première diapo dure 36,8 s : la voix expose l'horizon plat, puis deux mille
 * ans de démonstration, puis le plan de la capsule, alors que l'écran a tout
 * montré depuis la première seconde. Le spectateur lit avant d'entendre, et
 * n'a plus rien à découvrir pendant trente-cinq secondes.
 *
 * Une fraction de la durée ferait l'inverse sur les diapos longues : le
 * chiffre de chute attendrait dix-neuf secondes. Le rythme de lecture ne
 * dépend pas de la longueur du commentaire — d'où des secondes.
 *
 * Un bloc peut fixer son propre repère avec `data-at="7.5"` (secondes depuis
 * le début de la diapo) : c'est l'auteur qui sait quand sa phrase tombe.
 */
const BEATS_SECS = {
  rule: 0.5,
  lede: 1.0,
  stat: 4.5,
  statlabel: 5.0,
};

/**
 * Blocs posés d'emblée, sans animation propre.
 *
 * Deux familles, pour deux raisons :
 *
 * - `brand`, `num`, `grid` sont l'habillage de la capsule. Ils appartiennent au
 *   film, pas à la diapo ; les rejouer à chaque changement les faisait
 *   clignoter en bas de cadre.
 * - `kicker` et le titre sont l'identité de la diapo, et c'est le fondu enchaîné
 *   qui les amène. Quand ils avaient leur propre fondu, les deux se
 *   multipliaient : au quart du fondu, le titre entrant était à 5 % pendant que
 *   la diapo sortante avait déjà disparu sous le fond opaque de l'entrante.
 *   Mesuré sur la transition 1→2 : 0,33 % de pixels de contenu, soit un écran
 *   presque vide. Portés par le seul fondu, ils sont lisibles tout du long.
 */
const CARRIED_BY_TRANSITION = ['brand', 'num', 'grid', 'kicker', 'h1', 'h2'];

/** Dernier repère du barème, pour comprimer les diapos trop courtes. */
const LAST_BEAT_SECS = Math.max(...Object.values(BEATS_SECS)) + REVEAL_SECS;

const REVEAL_CSS = `
.capsule-slide { position: absolute; inset: 0; }

/* Le deck masque toutes les diapos sauf la courante ; ici chacune est seule. */
.capsule-slide .slides section { display: block !important; }

/*
 * --p : secondes écoulées depuis le début de la diapo.
 * --d : seconde à laquelle ce bloc se pose.
 * --c : compression appliquée quand la diapo est plus courte que le barème.
 * --k : 1 / durée de pose.
 */
.capsule-slide .slides section > * {
  --d: 1.6;
  --t: clamp(0, (var(--p) - var(--d) * var(--c)) * var(--k), 1);
  opacity: var(--t);
  transform: translateY(calc((1 - var(--t)) * 22px));
}

${Object.entries(BEATS_SECS)
  .map(([sel, at]) => {
    const target = sel.startsWith('h') ? sel : `.${sel}`;
    return `.capsule-slide .slides section > ${target} { --d: ${at}; }`;
  })
  .join('\n')}

/* Le filet se trace de gauche à droite sous le titre, il ne monte pas. */
.capsule-slide .slides section > .rule {
  transform: scaleX(var(--t));
  transform-origin: left center;
  opacity: 1;
}

/*
 * Le chiffre de chute est la récompense de la diapo : il monte d'un peu plus
 * haut et se pose en grandissant, pour qu'on le remarque sans un effet voyant.
 */
.capsule-slide .slides section > .stat {
  transform: translateY(calc((1 - var(--t)) * 30px)) scale(calc(0.965 + var(--t) * 0.035));
  transform-origin: left center;
}

${CARRIED_BY_TRANSITION.map((c) => {
  const target = c.startsWith('h') ? c : `.${c}`;
  return `.capsule-slide .slides section > ${target} { --d: 0; opacity: 1 !important; transform: none !important; }`;
}).join('\n')}

/*
 * La colonne de texte s'arrête avant le schéma. Sans cette borne, un titre
 * long court sous l'animation et les deux se brouillent.
 */
.capsule-slide .slides section > .kicker,
.capsule-slide .slides section > h1,
.capsule-slide .slides section > h2,
.capsule-slide .slides section > .lede,
.capsule-slide .slides section > .stat,
.capsule-slide .slides section > .statlabel { max-width: 40%; }
`;

/**
 * Schéma Manim de la diapo, joué derrière le texte.
 *
 * `OffthreadVideo` plutôt qu'une balise `<video>` : un rendu image par image a
 * besoin de l'image exacte, pas de celle que l'élément se trouve afficher.
 *
 * ## Ajuster le clip à la diapo
 *
 * Aucun clip ne dure exactement ce que dure sa diapo : la voix décide de la
 * durée de la diapo, Manim décide de celle du clip. Sur « Terre ronde » les
 * neuf clips étaient tous décalés — jusqu'à 6,4 s de trop peu sur une diapo de
 * 14,4 s, et 0,7 s de trop sur une autre.
 *
 * - **Clip trop long** : il était tronqué en pleine animation. On l'accélère
 *   juste assez pour qu'il finisse avec la diapo.
 * - **Clip trop court** : on l'étire pour qu'il occupe la diapo. Passé la fin
 *   du fichier, `OffthreadVideo` tient la dernière image ; c'est l'état final
 *   du schéma, celui qui porte l'information, mais six secondes de schéma figé
 *   pendant que la voix continue se voient.
 *
 * L'ajustement est borné : au-delà, la correction s'entendrait à l'œil — un
 * schéma précipité ou traînant est pire qu'un schéma qui finit un peu tôt.
 */
const Animation = ({ asset, durationInFrames, clipDurationInFrames }) => {
  const frame = useCurrentFrame();

  const fadeIn = interpolate(frame, [0, 18], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const fadeOut = interpolate(
    frame,
    [Math.max(0, durationInFrames - 14), durationInFrames],
    [1, 0],
    { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }
  );

  const clip = clipDurationInFrames || 0;
  const playbackRate =
    clip > 0
      ? Math.min(MAX_SPEEDUP, Math.max(MIN_SLOWDOWN, clip / durationInFrames))
      : 1;

  return (
    <AbsoluteFill style={{ opacity: Math.min(fadeIn, fadeOut) }}>
      <OffthreadVideo
        src={staticFile(asset)}
        muted
        playbackRate={playbackRate}
        style={{
          position: 'absolute',
          // Ancré à droite avec une marge, jamais en débord négatif : une
          // composition Manim est centrée et dessine ses étiquettes jusqu'à ses
          // propres bords. Ce qui dépassait du cadre était coupé, et plusieurs
          // capsules sont sorties avec des étiquettes tranchées à droite.
          right: '2%',
          top: '50%',
          width: '72%',
          transform: 'translateY(-50%)',
          ...DIAGRAM_MASK,
        }}
      />
    </AbsoluteFill>
  );
};

/**
 * Fondu des quatre bords du schéma.
 *
 * Un rendu Manim est une image opaque : son fond, presque noir mais pas tout à
 * fait celui de la page, dessine un rectangle net là où il s'arrête. Mesuré sur
 * la diapo 2 de « Terre ronde » : canevas à rgb(9,17,31) contre une page à
 * rgb(18,27,49) juste à côté — un écart faible, une arête parfaitement visible.
 *
 * Le bord gauche est le plus estompé : c'est celui qui rencontre la colonne de
 * texte. Les trois autres n'ont qu'à dissoudre l'arête. Fondre plutôt
 * qu'assortir les fonds : la page est un dégradé, aucune couleur fixe ne peut
 * s'y accorder partout, et un schéma peut arriver avec n'importe quel fond.
 */
const DIAGRAM_MASK = (() => {
  const layers = [
    'linear-gradient(to right, transparent 0%, black 14%, black 95%, transparent 100%)',
    'linear-gradient(to bottom, transparent 0%, black 7%, black 93%, transparent 100%)',
  ].join(', ');
  return {
    maskImage: layers,
    WebkitMaskImage: layers,
    // Les deux couches se recoupent : un pixel n'est gardé que s'il est à
    // l'intérieur des deux. Additionnées, elles ne masqueraient plus rien.
    maskComposite: 'intersect',
    WebkitMaskComposite: 'source-in',
  };
})();

const PAGE_BACKGROUND =
  'radial-gradient(120% 90% at 78% 42%, #16233c 0%, #0d1526 45%, #080e1a 100%)';

const Slide = ({ html, durationInFrames, manim, manimDurationInFrames, first }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const elapsedSecs = frame / fps;
  const slideSecs = durationInFrames / fps;

  // Une diapo plus courte que le barème resserre ses repères au lieu de ne
  // jamais poser ses derniers blocs.
  const compress = slideSecs >= LAST_BEAT_SECS ? 1 : slideSecs / LAST_BEAT_SECS;

  // Fondu entrant : la diapo précédente est encore montée dessous (sa séquence
  // est prolongée d'autant), donc l'enchaînement ne passe plus par le vide.
  const appear = first
    ? 1
    : interpolate(frame, [0, CROSSFADE_FRAMES], [0, 1], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      });

  // Le texte n'entre qu'une fois le fond de la diapo posé sur la précédente.
  //
  // Toutes les diapos placent leur titre au même endroit : un fondu croisé
  // franc y superpose deux titres à mi-course, et « L'ombre sur la Lune » par
  // dessus « Le navire qui s'enfonce » ne se lit plus du tout — c'est pire que
  // le trou qu'on cherchait à supprimer. Le fond, lui, recouvre le texte
  // sortant en montant : quand l'entrant arrive, la place est nette.
  const contentAppear = first
    ? 1
    : interpolate(appear, [CONTENT_GATE, 1], [0, 1], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      });

  return (
    <AbsoluteFill style={{ opacity: EASE_OUT(appear), background: PAGE_BACKGROUND }}>
      {manim ? (
        <Animation
          asset={manim}
          durationInFrames={durationInFrames}
          clipDurationInFrames={manimDurationInFrames}
        />
      ) : null}
      <div
        className="capsule-slide"
        style={{
          '--p': elapsedSecs,
          '--c': compress,
          '--k': 1 / REVEAL_SECS,
          opacity: EASE_OUT(contentAppear),
        }}
        // Rejoué dans la structure de conteneurs du deck : c'est sur
        // `.reveal .slides` que sa feuille de style accroche toute la mise en page.
        dangerouslySetInnerHTML={{
          __html: `<div class="reveal"><div class="slides">${html}</div></div>`,
        }}
      />
    </AbsoluteFill>
  );
};

/**
 * Barre de progression segmentée : une graduation par diapo.
 *
 * Un trait continu dit seulement « ça avance » ; les graduations disent en plus
 * combien de parties compte la capsule et où l'on en est — l'équivalent visuel
 * du sommaire qu'on n'a pas.
 */
const Progress = ({ boundaries }) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const pct = interpolate(frame, [0, durationInFrames], [0, 100], {
    extrapolateRight: 'clamp',
  });
  return (
    <>
      <div
        style={{
          position: 'absolute', left: 0, bottom: 0, height: 3, width: '100%',
          background: 'rgba(255,255,255,0.07)',
        }}
      />
      <div
        style={{
          position: 'absolute', left: 0, bottom: 0, height: 3, width: `${pct}%`,
          background: 'rgba(255,180,58,0.85)',
        }}
      />
      {boundaries.map((b) => (
        <div
          key={b}
          style={{
            position: 'absolute', bottom: 0, left: `${b * 100}%`, width: 2, height: 3,
            background: 'rgba(8,14,26,0.9)',
          }}
        />
      ))}
    </>
  );
};

/**
 * Réécrit `data-at="7.5"` en `--d: 7.5` sur le bloc concerné.
 *
 * CSS ne sait pas calculer à partir de la valeur d'un attribut ; la conversion
 * se fait donc ici, sur le balisage, avant l'injection.
 */
export const applyCues = (html) =>
  html.replace(/<([a-zA-Z][\w-]*)([^>]*?)\sdata-at="([\d.]+)"([^>]*)>/g, (m, tag, before, at, after) => {
    const decl = `--d: ${Number(at)};`;
    const all = `${before}${after}`;
    const styled = /\sstyle="/.test(all)
      ? all.replace(/\sstyle="/, ` style="${decl}`)
      : `${all} style="${decl}"`;
    return `<${tag}${styled}>`;
  });

export const Capsule = ({ slides = [], headHtml = '' }) => {
  // Le <head> du deck est rejoué pour que chaque diapo garde sa feuille de
  // style, ses polices et son identité. <title> est écarté : dans un corps de
  // page, il s'afficherait comme du texte.
  const head = headHtml
    .replace(/<\/?head>/g, '')
    .replace(/<title>[\s\S]*?<\/title>/g, '');

  const total = slides.reduce((n, s) => n + s.durationInFrames, 0) || 1;

  let from = 0;
  const placed = slides.map((slide) => {
    const start = from;
    from += slide.durationInFrames;
    return { slide, start };
  });

  const boundaries = placed.slice(1).map(({ start }) => start / total);

  return (
    <AbsoluteFill style={{ background: PAGE_BACKGROUND }}>
      <div dangerouslySetInnerHTML={{ __html: head }} />
      <style dangerouslySetInnerHTML={{ __html: REVEAL_CSS }} />
      {placed.map(({ slide, start }, i) => (
        <Sequence
          key={slide.index}
          from={start}
          // Prolongée du fondu : la diapo reste montée pendant que la suivante
          // apparaît par-dessus. Sans ce recouvrement, chaque changement passait
          // par un fondu au noir de vingt images.
          durationInFrames={
            slide.durationInFrames + (i < placed.length - 1 ? CROSSFADE_FRAMES : 0)
          }
          layout="none"
        >
          <Slide
            html={applyCues(slide.html)}
            durationInFrames={slide.durationInFrames}
            manim={slide.manim}
            manimDurationInFrames={slide.manimDurationInFrames}
            first={i === 0}
          />
        </Sequence>
      ))}
      <Progress boundaries={boundaries} />
    </AbsoluteFill>
  );
};
