package uz.sergak.api.provider;

import uz.sergak.api.model.ProviderResult;

/** Havola bo'yicha obro'ni tekshiruvchi manba. */
public interface UrlProvider {
    String name();

    boolean enabled();

    int priority();

    ProviderResult lookupUrl(String url);
}
