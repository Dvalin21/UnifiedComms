package com.unifiedcomms.di.qualifiers

import dagger.Qualifier
import javax.inject.Qualifier

@Qualifier
annotation class ApplicationContext

@Qualifier
annotation class MainThread

@Qualifier
annotation class IoThread