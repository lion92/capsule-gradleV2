import React from 'react';
import { Composition } from 'remotion';
import { Capsule } from './Capsule.jsx';

// Dimensions and length come from the props document written by the plugin;
// the placeholders here only exist so the composition can be selected before
// the real props are known.
export const Root = () => (
  <Composition
    id="Capsule"
    component={Capsule}
    durationInFrames={300}
    fps={30}
    width={1408}
    height={792}
    defaultProps={{ slides: [], headHtml: '', fps: 30 }}
  />
);
