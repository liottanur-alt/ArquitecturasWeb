package msvevento.service;

import java.text.Normalizer;
import java.util.Locale;

public final class CiudadNormalizada {
    private CiudadNormalizada() {}
    public static String de(String ciudad) {
        if (ciudad == null) return "";
        return Normalizer.normalize(ciudad, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
