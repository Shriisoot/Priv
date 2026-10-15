"""Synthesises the web sounds (mono Ogg Vorbis, as Minecraft needs for positional audio).

Run from the repo root:  python3 tools/generate_sounds.py   (requires numpy + ffmpeg with libvorbis)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "spiderman", "sounds")
RATE = 44100


def sweep_bandpass(noise, f_start, f_end, q, curve=2.0):
    """Resonant band-pass whose centre frequency glides from f_start to f_end."""
    n = len(noise)
    out = np.zeros(n)
    y1 = y2 = x1 = x2 = 0.0
    for i in range(n):
        t = (i / n) ** (1 / curve)
        f = f_start + (f_end - f_start) * t
        w0 = 2 * np.pi * f / RATE
        alpha = np.sin(w0) / (2 * q)
        b0, b2 = alpha, -alpha
        a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
        x0 = noise[i]
        y0 = (b0 * x0 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        out[i] = y0
        x2, x1 = x1, x0
        y2, y1 = y1, y0
    return out


def envelope(n, attack, decay):
    t = np.arange(n) / RATE
    env = np.minimum(1.0, t / attack) * np.exp(-np.maximum(0, t - attack) / decay)
    return env


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def normalise(x, peak=0.89):
    x = x - np.mean(x)
    fade = min(len(x), int(0.01 * RATE))
    x[-fade:] *= np.linspace(1, 0, fade)
    return x / np.max(np.abs(x)) * peak


def thwip(seed):
    rng = np.random.default_rng(seed)
    n = int(0.26 * RATE)
    noise = rng.standard_normal(n)
    pitch = 1.0 + (seed % 3 - 1) * 0.06
    zip_ = sweep_bandpass(noise, 4200 * pitch, 700 * pitch, q=3.5, curve=1.6) * envelope(n, 0.004, 0.055)
    t = np.arange(n) / RATE
    chirp_f = 1900 * pitch * np.exp(-t * 14) + 380
    chirp = np.sin(2 * np.pi * np.cumsum(chirp_f) / RATE) * envelope(n, 0.002, 0.035) * 0.35
    hiss = np.diff(noise, prepend=0) * envelope(n, 0.001, 0.012) * 0.25
    return normalise(zip_ * 1.0 + chirp + hiss)


def web_hit(seed):
    rng = np.random.default_rng(seed)
    n = int(0.22 * RATE)
    t = np.arange(n) / RATE
    thump_f = 150 * np.exp(-t * 9) + 70
    thump = np.sin(2 * np.pi * np.cumsum(thump_f) / RATE) * envelope(n, 0.002, 0.045)
    splat = lowpass(rng.standard_normal(n), 1800) * envelope(n, 0.001, 0.03) * 1.6
    stick = sweep_bandpass(rng.standard_normal(n), 2600, 1400, q=6) * envelope(n, 0.01, 0.05) * 0.5
    return normalise(thump * 0.9 + splat + stick)


def write_ogg(samples, path):
    pcm = (samples * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.remove(wav_path)


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(3):
        write_ogg(thwip(i), os.path.join(OUT, f"thwip{i + 1}.ogg"))
    for i in range(2):
        write_ogg(web_hit(10 + i), os.path.join(OUT, f"web_hit{i + 1}.ogg"))
    print("sounds written to", OUT)


if __name__ == "__main__":
    main()
