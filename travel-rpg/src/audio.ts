/* 音频系统初版（it-011 AC-2）：全 WebAudio 程序化合成，零音频资产。
   风（低通噪声+LFO 阵风）/ 鸟鸣（日间随机啾鸣）/ 湖岸水声（近湖渐入）/
   脚步（草/水两音色）/ 落地闷响 / 入水水花 / 划桨。
   AudioContext 需用户手势解锁（unlock 幂等，main 在首次按键/点击调用）。 */

export interface AudioEngine {
  unlock: () => void;
  setMuted: (m: boolean) => void;
  isMuted: () => boolean;
  isReady: () => boolean;
  readonly stepCount: number;
  step: (surface: 'grass' | 'water') => void;
  land: (force: number) => void;
  splash: () => void;
  paddle: () => void;
  tick: (dt: number, opts: { nearLake: number; day: boolean }) => void;
}

export function createAudio(): AudioEngine {
  let ctx: AudioContext | null = null;
  let master: GainNode | null = null;
  let muted = false;
  let lapGain: GainNode | null = null;
  let stepCount = 0;
  let nextChirp = 5;

  /* 噪声循环源（风/水声共用合成法） */
  function noiseSource(ac: AudioContext, seconds = 2): AudioBufferSourceNode {
    const buf = ac.createBuffer(1, ac.sampleRate * seconds, ac.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const src = ac.createBufferSource();
    src.buffer = buf;
    src.loop = true;
    return src;
  }

  /* 一次性噪声脉冲（脚步/水花/划桨共用） */
  function burst(o: {
    dur: number; vol: number;
    type: BiquadFilterType; freq: number; freqEnd?: number; q?: number;
    rate?: number;
  }): void {
    if (!ctx || !master) return;
    const t = ctx.currentTime;
    const src = noiseSource(ctx, o.dur + 0.05);
    src.playbackRate.value = o.rate ?? 1;
    const filter = ctx.createBiquadFilter();
    filter.type = o.type;
    filter.frequency.setValueAtTime(o.freq, t);
    if (o.freqEnd !== undefined) filter.frequency.exponentialRampToValueAtTime(o.freqEnd, t + o.dur);
    filter.Q.value = o.q ?? 1;
    const g = ctx.createGain();
    g.gain.setValueAtTime(o.vol, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + o.dur);
    src.connect(filter).connect(g).connect(master);
    src.start(t);
    src.stop(t + o.dur + 0.05);
  }

  function chirp(): void {
    if (!ctx || !master) return;
    const t0 = ctx.currentTime;
    const n = 2 + Math.floor(Math.random() * 2);
    for (let i = 0; i < n; i++) {
      const t = t0 + i * 0.16;
      const osc = ctx.createOscillator();
      const g = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(2300 + Math.random() * 500, t);
      osc.frequency.exponentialRampToValueAtTime(3400 + Math.random() * 400, t + 0.05);
      osc.frequency.exponentialRampToValueAtTime(2100, t + 0.1);
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.05, t + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.12);
      osc.connect(g).connect(master);
      osc.start(t);
      osc.stop(t + 0.14);
    }
  }

  const api: AudioEngine = {
    unlock() {
      if (ctx) {
        if (ctx.state === 'suspended') void ctx.resume();
        return;
      }
      const AC = window.AudioContext
        ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AC) return;
      ctx = new AC();
      master = ctx.createGain();
      master.gain.value = muted ? 0 : 0.9;
      master.connect(ctx.destination);

      /* 风：低通噪声 + 双 LFO 阵风 */
      const wind = noiseSource(ctx, 3);
      const windFilter = ctx.createBiquadFilter();
      windFilter.type = 'lowpass';
      windFilter.frequency.value = 420;
      const windGain = ctx.createGain();
      windGain.gain.value = 0.05;
      const lfo = ctx.createOscillator();
      lfo.frequency.value = 0.07;
      const lfoGain = ctx.createGain();
      lfoGain.gain.value = 0.028;
      lfo.connect(lfoGain).connect(windGain.gain);
      const lfo2 = ctx.createOscillator();
      lfo2.frequency.value = 0.23;
      const lfo2Gain = ctx.createGain();
      lfo2Gain.gain.value = 0.012;
      lfo2.connect(lfo2Gain).connect(windGain.gain);
      wind.connect(windFilter).connect(windGain).connect(master);
      wind.start();
      lfo.start();
      lfo2.start();

      /* 湖岸水声：带通噪声，音量随距离驱动（tick） */
      const lap = noiseSource(ctx, 2.4);
      const lapFilter = ctx.createBiquadFilter();
      lapFilter.type = 'bandpass';
      lapFilter.frequency.value = 640;
      lapFilter.Q.value = 0.8;
      lapGain = ctx.createGain();
      lapGain.gain.value = 0;
      lap.connect(lapFilter).connect(lapGain).connect(master);
      lap.start();
    },
    setMuted(m) {
      muted = m;
      if (master) master.gain.value = m ? 0 : 0.9;
    },
    isMuted: () => muted,
    isReady: () => ctx !== null,
    get stepCount() { return stepCount; },
    step(surface) {
      stepCount++;
      if (surface === 'water') {
        burst({ dur: 0.16, vol: 0.13, type: 'lowpass', freq: 620, rate: 0.9 + Math.random() * 0.2 });
      } else {
        burst({ dur: 0.07, vol: 0.09, type: 'bandpass', freq: 850 + Math.random() * 250, q: 1.2 });
      }
    },
    land(force) {
      burst({ dur: 0.16, vol: Math.min(0.05 + force * 0.03, 0.3), type: 'lowpass', freq: 300 });
    },
    splash() {
      burst({ dur: 0.5, vol: 0.26, type: 'bandpass', freq: 1300, freqEnd: 380, q: 0.7 });
      burst({ dur: 0.3, vol: 0.1, type: 'highpass', freq: 3000 });
    },
    paddle() {
      burst({ dur: 0.24, vol: 0.12, type: 'bandpass', freq: 900, freqEnd: 450, q: 0.9 });
    },
    tick(dt, opts) {
      if (!ctx || !lapGain) return;
      /* 湖岸水声渐入渐出 + 缓慢拍岸起伏 */
      const lapLfo = 0.7 + 0.3 * Math.sin(ctx.currentTime * 0.9);
      lapGain.gain.value += ((opts.nearLake * 0.05 * lapLfo) - lapGain.gain.value)
        * Math.min(1, dt * 3);
      /* 鸟鸣：日间随机 */
      if (opts.day) {
        nextChirp -= dt;
        if (nextChirp <= 0) {
          chirp();
          nextChirp = 4 + Math.random() * 5;
        }
      }
    },
  };
  return api;
}
