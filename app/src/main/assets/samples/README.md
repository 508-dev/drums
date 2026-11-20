# Snare sample provenance

All six files are from the [Versilian Community Sample Library](https://github.com/sgossner/VCSL),
created by Versilian Studios LLC and released under [CC0 1.0](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/LICENSE).
The source repository states its samples are CC0; the links below pin each
file to the reviewed repository revision. Retrieved 2026-11-19.

| Shipped file | Original source |
| --- | --- |
| ghost-1.wav | [Snare2_HitSN_v3_rr1_Mid.wav](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/Membranophones/Struck%20Membranophones/Snare%20Drum%2C%20Modern%201/Snare2_HitSN_v3_rr1_Mid.wav) |
| ghost-2.wav | [Snare2_HitSN_v3_rr2_Mid.wav](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/Membranophones/Struck%20Membranophones/Snare%20Drum%2C%20Modern%201/Snare2_HitSN_v3_rr2_Mid.wav) |
| normal-1.wav | [Snare2_HitSN_v6_rr1_Mid.wav](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/Membranophones/Struck%20Membranophones/Snare%20Drum%2C%20Modern%201/Snare2_HitSN_v6_rr1_Mid.wav) |
| normal-2.wav | [Snare2_HitSN_v6_rr2_Mid.wav](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/Membranophones/Struck%20Membranophones/Snare%20Drum%2C%20Modern%201/Snare2_HitSN_v6_rr2_Mid.wav) |
| accent-1.wav | [Snare2_HitSN_v9_rr1_Mid.wav](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/Membranophones/Struck%20Membranophones/Snare%20Drum%2C%20Modern%201/Snare2_HitSN_v9_rr1_Mid.wav) |
| accent-2.wav | [Snare2_HitSN_v9_rr2_Mid.wav](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/Membranophones/Struck%20Membranophones/Snare%20Drum%2C%20Modern%201/Snare2_HitSN_v9_rr2_Mid.wav) |

Each original was downmixed from stereo to mono, resampled from 44.1 to
48 kHz, trimmed to 500 ms starting 0.5 ms before its first 5%-of-peak sample,
peak-normalized to 0.8, and faded in over 0.5 ms and out over 10 ms. Output is
16-bit PCM. The app constructs buzz rolls from the first 30 ms of the ghost
hits; it does not ship a separate roll recording.

At load the app pads each file with up to 4 ms of silence in front, so that
its perceived attack (about 1.8–2.7 ms into the shipped file) is exactly 4 ms
in. The transport then starts it 4 ms early (DECISIONS.md, "A Snare's Attack
Is On The Beat").
