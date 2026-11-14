FROM python:3.12-slim

ARG PIPER_VOICE=en_US-lessac-medium
ENV PIPER_VOICE=${PIPER_VOICE}

RUN useradd --create-home --shell /bin/sh piper \
    && mkdir -p /voices \
    && chown -R piper:piper /voices

RUN pip install --no-cache-dir "piper-tts[http]"

USER piper
WORKDIR /home/piper

RUN python -m piper.download_voices "${PIPER_VOICE}" --data-dir /voices

EXPOSE 5000

CMD ["sh", "-c", "exec python -m piper.http_server -m \"${PIPER_VOICE}\" --data-dir /voices --host 0.0.0.0 --port 5000"]
