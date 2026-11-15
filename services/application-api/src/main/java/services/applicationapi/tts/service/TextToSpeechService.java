package services.applicationapi.tts.service;

public interface TextToSpeechService {

    byte[] synthesize(String text);
}
