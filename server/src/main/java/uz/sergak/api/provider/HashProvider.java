package uz.sergak.api.provider;

import uz.sergak.api.model.ProviderResult;

/** Fayl xeshi (SHA-256) bo'yicha obro'ni tekshiruvchi manba. */
public interface HashProvider {
    String name();

    boolean enabled();

    /** Kichik raqam — avval chaqiriladi (kvotasi kengroq manbalar oldin). */
    int priority();

    ProviderResult lookupHash(String sha256);
}
