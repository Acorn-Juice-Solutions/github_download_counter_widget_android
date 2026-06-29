package com.example.downloadwidget

class DownloadWidgetSmallProvider : BaseDownloadWidgetProvider() {
    override val layoutId: Int = R.layout.widget_layout_small
    override val providerClass: Class<*> = DownloadWidgetSmallProvider::class.java
}
