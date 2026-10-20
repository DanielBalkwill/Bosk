package com.generic.mnemos.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Application-wide Hilt bindings. SecureKeyStore is auto-provided via its @Inject
 * constructor, so nothing is needed here. Concrete singletons live in DataModule; interface
 * bindings (incl. the LlmProvider multibinding map) live in BindingsModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
