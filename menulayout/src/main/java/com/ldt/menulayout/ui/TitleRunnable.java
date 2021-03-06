package com.ldt.menulayout.ui;

public abstract class TitleRunnable implements Runnable {
    private final String mTitle;
    private final String mDescription;

    public TitleRunnable(String title, String description) {
        mTitle = title;
        mDescription = description;
    }
}
