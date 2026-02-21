# Noise Generator 🎛️

Minimal Kotlin HTTP server that serves random RGB noise as a JPEG or an MJPG stream
with a UTC time overlay.

```
🖼️  JPEG  ──>  /api/noise.jpg
🎞️  MJPG  ──>  /api/noise.mjpg
⏱️  UTC   ──>  HH:mm:ss
```

## Run locally 🚀

Set `PORT` if needed, otherwise defaults to 8080.

```bash
kotlinc src/*.kt -include-runtime -d app.jar
java -jar app.jar
```

## API 🧩

### Endpoints

| Route | Description |
| --- | --- |
| `GET /` | Rendered README (HTML) |
| `GET /?type=jpg` | Single JPEG (legacy-style root behavior) |
| `GET /?type=mjpg` | MJPG stream (legacy-style root behavior) |
| `GET /health` | Health check (`ok`) |
| `GET /stream.cgi` | Legacy MJPG stream endpoint |
| `GET /api/noise.jpg?width=320&height=240` | Single JPEG image |
| `GET /api/noise.mjpg?width=320&height=240&fps=10` | MJPG stream (`multipart/x-mixed-replace`) |

### Parameters

| Param | Applies to | Default | Range | Purpose |
| --- | --- | --- | --- | --- |
| `type` | `/` | none | `jpg`, `mjpg` | Legacy-style root response |
| `width` | jpg, mjpg | 320 | 16..4096 (jpg), 16..2048 (mjpg) | Image width |
| `height` | jpg, mjpg | 240 | 16..4096 (jpg), 16..2048 (mjpg) | Image height |
| `fps` | mjpg | 10 | 1..60 | Frames per second |
| `variant` | mjpg | standard | see variants | Modern framing behavior |
| `pad` | mjpg | 16 | 0..256 | Padding bytes before boundary (`offset`) |
| `chunk` | mjpg | 1024 | 128..8192 | Chunk size (`split`) |
| `pre` | mjpg | 16 | 0..256 | Bytes before first boundary (`preamble`) |
| `post` | mjpg | 16 | 0..256 | Bytes after each frame (`postamble`) |
| `mjpgInterval` | mjpg (legacy) | 100 | 100..10000 | Interval between frames (ms) |
| `mjpgMod` | mjpg (legacy) | none | `offset`, `padd` | Legacy aliases (mapped to modern variants) |
| `mjpgHeaderMod` | mjpg (legacy) | none | `noLength`, `zeroLength` | Legacy header tweaks |
| `auth` | all | none | `basic`, `digest` | Enable auth challenge |
| `cors` | all | false | true/false | Enable `Access-Control-Allow-Origin: *` |
| `exposeAuthHeader` | all | true | true/false | Expose `WWW-Authenticate` header |

### Quick examples

| Goal | URL |
| --- | --- |
| Plain JPG | `/api/noise.jpg` |
| 1080p JPG | `/api/noise.jpg?width=1920&height=1080` |
| Fast MJPG | `/api/noise.mjpg?fps=30` |
| Chunked MJPG | `/api/noise.mjpg?variant=split&chunk=512` |
| Offset MJPG | `/api/noise.mjpg?variant=offset&pad=32` |
| Broken boundary | `/api/noise.mjpg?variant=wrong-boundary` |
| Legacy stream | `/stream.cgi?mjpgInterval=150` |
| Legacy offset | `/stream.cgi?mjpgMod=offset,padd` |
| Basic auth | `/api/noise.jpg?auth=basic` |
| Digest auth | `/api/noise.mjpg?auth=digest` |

## MJPG variants 🧪

### `standard` ✅

Clean boundaries and headers.

```
--frame\r\n
Content-Type: image/jpeg\r\n
Content-Length: 12345\r\n
\r\n
[JPEG BYTES...]\r\n
```

### `offset` 🧱

Padding bytes before each boundary to simulate extra bytes between parts.

```
................
--frame\r\n
Content-Type: image/jpeg\r\n
Content-Length: 12345\r\n
\r\n
[JPEG BYTES...]\r\n
```

### `split` 🧩

Boundary and JPEG body written in smaller chunks to simulate misaligned reads.

```
--fr
ame\r\n
Content-Type: image/jpeg\r\n
Content-Length: 12345\r\n
\r\n
[JPE][G BY][TES...]\r\n
```

### `nocl` 📏✖

Omit `Content-Length`, rely on boundary only.

```
--frame\r\n
Content-Type: image/jpeg\r\n
\r\n
[JPEG BYTES...]\r\n
```

### `no-crlf` ↩️

Use LF instead of CRLF between lines.

```
--frame\n
Content-Type: image/jpeg\n
Content-Length: 12345\n
\n
[JPEG BYTES...]\n
```

### `wrong-boundary` ⚠️

Header says one boundary, body uses another.

```
Content-Type: multipart/x-mixed-replace; boundary=frame

--wrongframe\r\n
Content-Type: image/jpeg\r\n
Content-Length: 12345\r\n
\r\n
[JPEG BYTES...]\r\n
```

### `preamble` 🚧

Bytes before the first boundary.

```
pppppppppppppppp
--frame\r\n
Content-Type: image/jpeg\r\n
Content-Length: 12345\r\n
\r\n
[JPEG BYTES...]\r\n
```

### `postamble` 🧹

Bytes after each frame.

```
--frame\r\n
Content-Type: image/jpeg\r\n
Content-Length: 12345\r\n
\r\n
[JPEG BYTES...]\r\n
tttttttttttttttt
```

## Legacy MJPG mods (Express-style) 🧯

Legacy modes are triggered by `mjpgMod`, `mjpgHeaderMod`, or `mjpgInterval`
and are primarily intended to mirror the Express sample behavior. Some legacy
mods are mapped to modern variants:

| Legacy mod | Modern equivalent |
| --- | --- |
| `mjpgMod=offset` | `variant=offset` |
| `mjpgMod=padd` | `variant=postamble&post=512` |

On `/api/noise.mjpg`, these legacy mods act as aliases to the modern variants.
`mjpgInterval` on `/api/noise.mjpg` is converted to an FPS equivalent.

### `mjpgHeaderMod=noLength`

Includes a `Content-length` header with the actual length.

### `mjpgHeaderMod=zeroLength`

Includes `Content-length: 0` regardless of payload size.

## Auth 🔐

Enable auth with `auth=basic` or `auth=digest`.

Default credentials:

- Username: `user`
- Password: `password`

The time overlay is formatted as `HH:mm:ss` in UTC.

## Docker 🐳

```bash
docker build -t kotlin-noise-generator .
docker run --rm -p 8080:8080 kotlin-noise-generator
```
