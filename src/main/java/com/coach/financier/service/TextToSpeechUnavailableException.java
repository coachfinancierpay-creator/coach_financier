package com.coach.financier.service;

/** Échec attendu de configuration ou d'appel au fournisseur de synthèse vocale. */
public class TextToSpeechUnavailableException extends RuntimeException {
    public TextToSpeechUnavailableException(String message) {
        super(message);
    }

    public TextToSpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}