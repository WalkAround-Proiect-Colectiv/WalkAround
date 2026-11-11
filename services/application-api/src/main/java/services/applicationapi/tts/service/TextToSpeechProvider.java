package services.applicationapi.tts.service;

public interface TextToSpeechProvider {

    byte[] synthesize(String text);
}
