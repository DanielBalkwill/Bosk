package com.generic.mnemos.core.ai.llm

import dagger.MapKey

/** Dagger multibinding key so providers can be injected as a `Map<ProviderId, LlmProvider>`. */
@MapKey
annotation class ProviderKey(val value: ProviderId)
