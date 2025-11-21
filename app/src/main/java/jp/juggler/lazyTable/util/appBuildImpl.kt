package jp.juggler.lazyTable.util

interface AppBuild {
    val isDebug: Boolean
    val isRelease: Boolean
}

val appBuild by lazy { AppBuildImpl() }
