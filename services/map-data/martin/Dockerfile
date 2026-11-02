FROM alpine:3.22 AS fonts
RUN apk add --no-cache font-noto
FROM ghcr.io/maplibre/martin:1.16.0
COPY --from=fonts /usr/share/fonts/noto/NotoSans-Regular.ttf /fonts/NotoSans-Regular.ttf