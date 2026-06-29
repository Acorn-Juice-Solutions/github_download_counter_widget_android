package com.example.downloadwidget

class DownloadWidgetProvider : BaseDownloadWidgetProvider() {
    override val layoutId: Int = R.layout.widget_layout
    override val providerClass: Class<*> = DownloadWidgetProvider::class.java
}
