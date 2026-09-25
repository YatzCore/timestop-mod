import numpy as np
import scipy.io.wavfile as wav
import scipy.signal as signal
import subprocess
import os

sr = 44100

def save_ogg(samples, path):
    peak = np.max(np.abs(samples))
    if peak > 0:
        samples = samples / peak * 0.92
    int_samples = (samples * 32767).astype(np.int16)
    wav_path = path.replace('.ogg', '.wav')
    wav.write(wav_path, sr, int_samples)
    cmd = ['ffmpeg', '-y', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '6', path]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if os.path.exists(wav_path):
        os.remove(wav_path)
    print(f"Generated {path}: {os.path.getsize(path)} bytes")

def gen_pedestal_insert():
    duration = 0.85
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)
    out = np.zeros_like(t)

    # 1. Three crisp clockwork gear clicks
    for click_t in [0.01, 0.035, 0.065]:
        dt = t - click_t
        mask = (dt >= 0) & (dt < 0.03)
        click = np.sin(2 * np.pi * 3200 * dt[mask]) * np.exp(-dt[mask] / 0.005)
        # Add slight white noise click
        noise = (np.random.rand(len(dt[mask])) * 2 - 1) * np.exp(-dt[mask] / 0.004)
        out[mask] += 0.45 * click + 0.35 * noise

    # 2. Resonant magical clock chime chords (C5, E5, G5, B5, C6)
    chime_start = 0.05
    dt_chime = t - chime_start
    chime_mask = dt_chime >= 0
    t_ch = dt_chime[chime_mask]

    freqs = [523.25, 659.25, 783.99, 987.77, 1046.50]
    weights = [0.35, 0.28, 0.25, 0.18, 0.15]
    taus = [0.35, 0.30, 0.28, 0.22, 0.20]

    chime_sig = np.zeros_like(t_ch)
    for f, w, tau in zip(freqs, weights, taus):
        # Detuned stereo pairs for crystalline width
        osc = np.sin(2 * np.pi * f * t_ch) + 0.3 * np.sin(2 * np.pi * (f * 2.01) * t_ch)
        decay = np.exp(-t_ch / tau)
        chime_sig += w * osc * decay

    out[chime_mask] += chime_sig

    # Fade out smoothly at tail
    fade_len = int(sr * 0.05)
    out[-fade_len:] *= np.linspace(1, 0, fade_len)
    return out

def gen_pedestal_activate():
    # Power surge + rising laser beam shoot whoosh + impact hit
    duration = 1.6
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)
    out = np.zeros_like(t)

    # 1. Sub-bass power surge sweeping 45 Hz -> 160 Hz (0 to 0.45s)
    f_start, f_mid = 45.0, 160.0
    sweep_t = np.clip(t / 0.45, 0, 1)
    phase_sub = 2 * np.pi * (f_start * t + 0.5 * (f_mid - f_start) * t * sweep_t)
    sub_env = np.sin(np.pi * np.clip(t / 0.5, 0, 1)) ** 1.5
    sub_bass = np.tanh(2.5 * np.sin(phase_sub)) * sub_env * 0.7
    out += sub_bass

    # 2. Ascending plasma beam shoot whoosh (0.1s to 0.75s)
    # Bandpass filtered noise sweeping upward from 350 Hz to 4200 Hz
    noise = np.random.randn(len(t))
    # Synthetic rising formant
    f_shoot = 350.0 + 3850.0 * (np.clip((t - 0.1) / 0.65, 0, 1) ** 2.2)
    shoot_env = np.zeros_like(t)
    shoot_mask = (t >= 0.1) & (t <= 0.8)
    st = (t[shoot_mask] - 0.1) / 0.7
    shoot_env[shoot_mask] = np.clip(np.sin(np.pi * st), 0, None) ** 0.8

    # Modulated high-frequency beam sweep
    phase_beam = 2 * np.pi * np.cumsum(f_shoot) / sr
    beam_harmonic = (np.sin(phase_beam) + 0.5 * np.sin(phase_beam * 1.5) + 0.25 * np.sin(phase_beam * 2.5))
    beam_sig = (beam_harmonic * 0.4 + noise * 0.45) * shoot_env
    out += beam_sig * 0.65

    # 3. Beam arrival / impact detonation (at t = 0.65s when beam hits shell)
    t_imp = t - 0.62
    imp_mask = t_imp >= 0
    ti = t_imp[imp_mask]
    # Deep impact thump + shimmer
    imp_thump = np.sin(2 * np.pi * 75 * np.exp(-ti / 0.08) * ti) * np.exp(-ti / 0.18)
    imp_shimmer = np.sin(2 * np.pi * 1567.98 * ti) * np.exp(-ti / 0.4) + np.sin(2 * np.pi * 2093.0 * ti) * np.exp(-ti / 0.35)
    out[imp_mask] += 0.6 * imp_thump + 0.35 * imp_shimmer

    # Smooth tail fade
    fade_len = int(sr * 0.1)
    out[-fade_len:] *= np.linspace(1, 0, fade_len)
    return out

def gen_pedestal_ambient():
    # Continuous low hum: fundamental at 60 Hz (exactly 240 cycles in 4.0s)
    # Perfectly seamless loop at 4.0 seconds
    duration = 4.0
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)

    f0 = 60.0 # Hz
    # Subtle 0.5 Hz phasing modulation (exactly 2 full cycles in 4.0s)
    mod_phase = 0.35 * np.sin(2 * np.pi * 0.5 * t)
    
    # Fundamental and rich harmonics
    h1 = np.sin(2 * np.pi * f0 * t + mod_phase)
    h2 = 0.45 * np.sin(2 * np.pi * (2 * f0) * t + mod_phase * 1.5)
    h3 = 0.22 * np.sin(2 * np.pi * (3 * f0) * t)
    h4 = 0.12 * np.sin(2 * np.pi * (4 * f0) * t)
    
    # Warm analog saturation for full, comforting bass presence
    raw_hum = h1 + h2 + h3 + h4
    warm_hum = np.tanh(1.8 * raw_hum) / 1.8

    # Subtle crystalline temporal resonance (540 Hz = 9th harmonic of 60Hz, exactly 2160 cycles)
    crystal = 0.04 * np.sin(2 * np.pi * 540.0 * t) * (1.0 + 0.3 * np.sin(2 * np.pi * 1.0 * t))
    
    out = warm_hum * 0.85 + crystal

    # Start and end match perfectly because 60Hz, 120Hz, 180Hz, 240Hz, 540Hz and 0.5Hz modulation
    # all have an exact integer number of cycles in 4.0 seconds!
    return out

def gen_pedestal_deactivate():
    # Power-down / magnetic decoupling / field collapse
    duration = 1.0
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)
    out = np.zeros_like(t)

    # 1. Mechanical snap at t=0
    dt_snap = t
    snap_mask = dt_snap < 0.04
    out[snap_mask] += (np.sin(2 * np.pi * 1800 * dt_snap[snap_mask]) + 0.5 * np.random.randn(np.sum(snap_mask))) * np.exp(-dt_snap[snap_mask] / 0.008) * 0.45

    # 2. Descending frequency drop sweep: 180 Hz down to 32 Hz over 0.55s
    f_decay = 32.0 + (180.0 - 32.0) * np.exp(-t / 0.16)
    phase_down = 2 * np.pi * np.cumsum(f_decay) / sr
    env_down = np.exp(-t / 0.28) * (1.0 - np.exp(-t / 0.02))
    hum_down = np.sin(phase_down) * env_down * 0.75

    # 3. Soft vacuum dissipation whoosh
    noise = np.random.randn(len(t))
    whoosh_env = (t / 0.15) * np.exp(-t / 0.22)
    whoosh = noise * whoosh_env * 0.35

    out += hum_down + whoosh
    fade_len = int(sr * 0.1)
    out[-fade_len:] *= np.linspace(1, 0, fade_len)
    return out

def main():
    target_dir = os.path.abspath("common/src/main/resources/assets/timestop/sounds")
    os.makedirs(target_dir, exist_ok=True)

    print("Synthesizing custom pedestal sound effects...")
    save_ogg(gen_pedestal_insert(), os.path.join(target_dir, "pedestal_insert.ogg"))
    save_ogg(gen_pedestal_activate(), os.path.join(target_dir, "pedestal_activate.ogg"))
    save_ogg(gen_pedestal_ambient(), os.path.join(target_dir, "pedestal_ambient.ogg"))
    save_ogg(gen_pedestal_deactivate(), os.path.join(target_dir, "pedestal_deactivate.ogg"))
    print("All sounds generated successfully!")

if __name__ == "__main__":
    main()
