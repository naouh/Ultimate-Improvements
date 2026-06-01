package com.nao.serverguide.config;

/** One tab of the guide: a display title and the raw (markup) text loaded from its file. */
public final class GuidePage {
    public final String title;
    public final String fileName;
    public final String text;

    public GuidePage(String title, String fileName, String text) {
        this.title = title;
        this.fileName = fileName;
        this.text = text;
    }
}
