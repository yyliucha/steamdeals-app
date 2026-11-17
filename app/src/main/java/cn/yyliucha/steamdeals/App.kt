package cn.yyliucha.steamdeals

import android.app.Application
import cn.yyliucha.steamdeals.di.ServiceLocator

class App : Application() {
    val locator: ServiceLocator by lazy { ServiceLocator.get(this) }

    override fun onCreate() {
        super.onCreate()
        locator
    }
}
