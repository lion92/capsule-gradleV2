/**
 * Render entry point used by capsule-gradle's RemotionCaptureImpl.
 *
 * Reads the props document written by the plugin, sizes the composition from
 * it, and renders a silent WebM. Narration is muxed on by the plugin
 * afterwards, exactly as for the other capture strategies.
 *
 *   node render.mjs --props <file.json> --out <file.webm> --concurrency <n>
 */
import { bundle } from '@remotion/bundler';
import { renderMedia, selectComposition } from '@remotion/renderer';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i === -1 ? fallback : process.argv[i + 1];
}

const propsFile = arg('props');
const outFile = arg('out');
const concurrency = Number(arg('concurrency', '4'));
const codec = arg('codec', 'vp8');

if (!propsFile || !outFile) {
  console.error('usage: node render.mjs --props <file.json> --out <file.webm> [--concurrency n]');
  process.exit(2);
}

const props = JSON.parse(fs.readFileSync(propsFile, 'utf8'));

function findChromium() {
  const candidates = [
    process.env.CAPSULE_CHROMIUM,
    '/usr/bin/chromium-browser',
    '/usr/bin/chromium',
    '/usr/bin/google-chrome',
  ].filter(Boolean);
  for (const c of candidates) {
    try {
      execSync(`test -f ${c}`);
      return c;
    } catch {}
  }
  return undefined;
}

const browserExecutable = findChromium();

const bundleLocation = await bundle({ entryPoint: path.join(__dirname, 'src/index.js') });

const composition = await selectComposition({
  serveUrl: bundleLocation,
  id: 'Capsule',
  inputProps: props,
  browserExecutable,
});

composition.width = props.width;
composition.height = props.height;
composition.fps = props.fps;
composition.durationInFrames = props.totalFrames;

console.log(
  `Rendering ${props.slides.length} slides, ${props.totalFrames} frames ` +
  `at ${props.fps} fps, concurrency ${concurrency}, codec ${codec}`
);

const started = Date.now();
let lastLogged = 0;

await renderMedia({
  composition,
  serveUrl: bundleLocation,
  codec,
  outputLocation: outFile,
  inputProps: props,
  concurrency,
  browserExecutable,
  // Progress goes to stdout, which the plugin redirects to a log file. Without
  // it a long render is indistinguishable from a hung one.
  onProgress: ({ renderedFrames, encodedFrames }) => {
    const now = Date.now();
    if (now - lastLogged < 5000) return;
    lastLogged = now;
    const elapsed = (now - started) / 1000;
    const fps = renderedFrames / Math.max(elapsed, 0.001);
    const remaining = (props.totalFrames - renderedFrames) / Math.max(fps, 0.001);
    console.log(
      `  ${renderedFrames}/${props.totalFrames} rendues, ${encodedFrames} encodées ` +
      `— ${fps.toFixed(1)} img/s — reste ~${Math.round(remaining)} s`
    );
  },
  // A root Chromium cannot initialise its sandbox and hangs instead of failing;
  // Gradle builds routinely run as root in containers and CI runners.
  chromiumOptions: { gl: 'swangle', ignoreCertificateErrors: false },
  browserArgs: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
});

console.log(
  `done → ${outFile} (${((Date.now() - started) / 1000).toFixed(1)} s, ` +
  `${(props.totalFrames / ((Date.now() - started) / 1000)).toFixed(1)} img/s)`
);
