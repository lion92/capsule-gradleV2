import React from 'react';
import { AbsoluteFill, Sequence, OffthreadVideo, staticFile, useCurrentFrame, useVideoConfig, interpolate } from 'remotion';

/**
 * Animation applied on top of a deck slide.
 *
 * Everything is driven by the frame number rather than by CSS animations: a
 * frame-by-frame renderer seeks arbitrarily, and time-based animations do not
 * reproduce identically when it does.
 */

const EASE = (t) => 1 - Math.pow(1 - t, 3);

/**
 * Staggered reveal of every direct child of the slide, driven by --p.
 *
 * The selector goes through `section` on purpose: the deck's own stylesheet
 * lays slides out through `.reveal .slides section`, so the section is replayed
 * inside that structure and what animates is its contents, not the slide block.
 */
const REVEAL_CSS = `
.capsule-slide { position: absolute; inset: 0; }
.capsule-slide .slides section > * {
  --d: 0;
  opacity: clamp(0, (var(--p) - var(--d)) * 3.2, 1);
  transform: translateY(calc((1 - clamp(0, (var(--p) - var(--d)) * 3.2, 1)) * 26px));
}
.capsule-slide .slides section > *:nth-child(1) { --d: 0.00; }
.capsule-slide .slides section > *:nth-child(2) { --d: 0.06; }
.capsule-slide .slides section > *:nth-child(3) { --d: 0.12; }
.capsule-slide .slides section > *:nth-child(4) { --d: 0.18; }
.capsule-slide .slides section > *:nth-child(5) { --d: 0.24; }
.capsule-slide .slides section > *:nth-child(6) { --d: 0.30; }
.capsule-slide .slides section > *:nth-child(7) { --d: 0.36; }
.capsule-slide .slides section > *:nth-child(8) { --d: 0.42; }
.capsule-slide .slides section > *:nth-child(n+9) { --d: 0.48; }
/* The grid backdrop must not slide in with the content. */
.capsule-slide .slides section > .grid { --d: 0; opacity: 1 !important; transform: none !important; }
/* The deck hides every slide but the current one; here each section is alone. */
.capsule-slide .slides section { display: block !important; }
`;

/**
 * Manim animation of the slide, played behind the text.
 *
 * Anchored right and scaled down so its centred composition lands clear of the
 * text column. OffthreadVideo rather than a plain <video>: a frame-by-frame
 * renderer needs the exact frame, not whatever the element happens to show.
 */
const Animation = ({ asset, durationInFrames }) => {
  const frame = useCurrentFrame();
  const fade = interpolate(frame, [0, 18], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const out = interpolate(
    frame,
    [Math.max(0, durationInFrames - 14), durationInFrames],
    [1, 0],
    { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }
  );
  return (
    <AbsoluteFill style={{ opacity: Math.min(fade, out) }}>
      <OffthreadVideo
        src={staticFile(asset)}
        muted
        style={{
          position: 'absolute',
          right: '-6%',
          top: '50%',
          width: '82%',
          transform: 'translateY(-50%)',
          // Sans ce fondu, le bord gauche de la vidéo coupe net les aplats des
          // schémas (l'horizon marin, par exemple) en plein milieu de l'image.
          maskImage: 'linear-gradient(to right, transparent 0%, black 16%)',
          WebkitMaskImage: 'linear-gradient(to right, transparent 0%, black 16%)',
        }}
      />
    </AbsoluteFill>
  );
};

const Slide = ({ html, durationInFrames, manim }) => {
  const frame = useCurrentFrame();

  // Entrance: children rise and fade in, staggered by their position.
  const p = EASE(interpolate(frame, [0, Math.min(28, durationInFrames)], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  }));

  // Slow drift over the whole slide, so a long narration never sits on a
  // perfectly frozen image.
  const scale = interpolate(frame, [0, durationInFrames], [1, 1.028], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // Cross-fade out over the last frames; the next slide is already underneath.
  const fadeOut = interpolate(
    frame,
    [Math.max(0, durationInFrames - 16), durationInFrames],
    [1, 0],
    { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }
  );

  return (
    <AbsoluteFill style={{ opacity: fadeOut, transform: `scale(${scale})` }}>
      {manim ? <Animation asset={manim} durationInFrames={durationInFrames} /> : null}
      <div
        className="capsule-slide"
        style={{ '--p': p }}
        // Replayed inside the deck's own container structure: `.reveal .slides`
        // is what its stylesheet keys the whole layout on.
        dangerouslySetInnerHTML={{
          __html: `<div class="reveal"><div class="slides">${html}</div></div>`,
        }}
      />
    </AbsoluteFill>
  );
};

/** Thin progress bar: gives the viewer a sense of where the capsule is going. */
const Progress = () => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const pct = interpolate(frame, [0, durationInFrames], [0, 100], {
    extrapolateRight: 'clamp',
  });
  return (
    <div style={{ position: 'absolute', left: 0, bottom: 0, height: 3, width: `${pct}%`,
                  background: 'rgba(255,180,58,0.75)' }} />
  );
};

export const Capsule = ({ slides = [], headHtml = '' }) => {
  // The deck's own <head> is replayed so each slide keeps its stylesheet,
  // fonts and identity. <title> is dropped: it renders as text in a body.
  const head = headHtml
    .replace(/<\/?head>/g, '')
    .replace(/<title>[\s\S]*?<\/title>/g, '');

  let from = 0;
  return (
    <AbsoluteFill
      style={{
        // Dégradé plutôt qu'un aplat : le quadrillage précédent alourdissait
        // l'image et entrait en concurrence avec les schémas.
        background:
          'radial-gradient(120% 90% at 78% 42%, #16233c 0%, #0d1526 45%, #080e1a 100%)',
      }}
    >
      <div dangerouslySetInnerHTML={{ __html: head }} />
      <style dangerouslySetInnerHTML={{ __html: REVEAL_CSS }} />
      {slides.map((slide) => {
        const start = from;
        from += slide.durationInFrames;
        return (
          <Sequence
            key={slide.index}
            from={start}
            durationInFrames={slide.durationInFrames}
            layout="none"
          >
            <Slide
              html={slide.html}
              durationInFrames={slide.durationInFrames}
              manim={slide.manim}
            />
          </Sequence>
        );
      })}
      <Progress />
    </AbsoluteFill>
  );
};
