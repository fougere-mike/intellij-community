// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.ide.brs

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.functions.functionInterfacePackageFragmentProvider
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.caches.resolve.IdePlatformKindResolution
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.ide.brs.analyzer.BrsResolverForModuleFactory
import org.jetbrains.kotlin.ide.konan.createKlibPackageFragmentProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.caches.resolve.BuiltInsCacheKey
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.library.metadata.NullFlexibleTypeDeserializer
import org.jetbrains.kotlin.library.metadata.impl.KlibMetadataModuleDescriptorFactoryImpl
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.BrsIdePlatformKind
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.storage.StorageManager

private val LOG = Logger.getInstance(BrsPlatformKindResolution::class.java)

class BrsPlatformKindResolution : IdePlatformKindResolution {
    override fun createKlibPackageFragmentProvider(
        moduleInfo: ModuleInfo,
        storageManager: StorageManager,
        languageVersionSettings: LanguageVersionSettings,
        moduleDescriptor: ModuleDescriptor
    ): PackageFragmentProvider? {
        return (moduleInfo as? BrsKlibLibraryInfo)
            ?.resolvedKotlinLibrary
            ?.createKlibPackageFragmentProvider(
                storageManager = storageManager,
                metadataModuleDescriptorFactory = metadataFactories.DefaultDeserializedDescriptorFactory,
                languageVersionSettings = languageVersionSettings,
                moduleDescriptor = moduleDescriptor,
                lookupTracker = LookupTracker.DO_NOTHING
            )
    }

    override fun createResolverForModuleFactory(
        settings: PlatformAnalysisParameters,
        environment: TargetEnvironment,
        platform: TargetPlatform
    ): ResolverForModuleFactory {
        return BrsResolverForModuleFactory(settings, environment, platform)
    }

    override val kind get() = BrsIdePlatformKind

    override fun getKeyForBuiltIns(moduleInfo: ModuleInfo, sdkInfo: SdkInfo?, stdlibInfo: LibraryInfo?): BuiltInsCacheKey = BrsBuiltInsCacheKey

    override fun createBuiltIns(
        moduleInfo: IdeaModuleInfo,
        projectContext: ProjectContext,
        resolverForProject: ResolverForProject<IdeaModuleInfo>,
        sdkDependency: SdkInfo?,
        stdlibDependency: LibraryInfo?,
    ) = createBrsBuiltIns(moduleInfo, projectContext)

    private fun createBrsBuiltIns(moduleInfo: ModuleInfo, projectContext: ProjectContext): KotlinBuiltIns {
        LOG.warn("BRS: createBrsBuiltIns called for moduleInfo: ${moduleInfo.name}")
        val stdlibInfo = moduleInfo.findBrsStdlib()
        if (stdlibInfo == null) {
            LOG.warn("BRS: findBrsStdlib returned null, falling back to DefaultBuiltIns")
            return DefaultBuiltIns.Instance
        }
        LOG.warn("BRS: Found stdlib: ${stdlibInfo.libraryRoot}, isStdlib=${stdlibInfo.isStdlib}, compatible=${stdlibInfo.compatibilityInfo.isCompatible}")

        val project = projectContext.project
        val storageManager = projectContext.storageManager

        val builtInsModule = metadataFactories.DefaultDescriptorFactory.createDescriptorAndNewBuiltIns(
            KotlinBuiltIns.BUILTINS_MODULE_NAME,
            storageManager,
            DeserializedKlibModuleOrigin(stdlibInfo.resolvedKotlinLibrary),
            stdlibInfo.capabilities
        )

        val languageVersionSettings = project.service<LanguageSettingsProvider>().getLanguageVersionSettings(stdlibInfo, project)

        LOG.warn("BRS: Creating klib package fragment provider for stdlib")
        val stdlibPackageFragmentProvider = createKlibPackageFragmentProvider(
            stdlibInfo,
            storageManager,
            languageVersionSettings,
            builtInsModule
        )
        if (stdlibPackageFragmentProvider == null) {
            LOG.warn("BRS: createKlibPackageFragmentProvider returned null! Falling back to DefaultBuiltIns")
            return DefaultBuiltIns.Instance
        }
        LOG.warn("BRS: Successfully created package fragment provider: $stdlibPackageFragmentProvider")

        builtInsModule.initialize(
            CompositePackageFragmentProvider(
                listOf(
                    stdlibPackageFragmentProvider,
                    functionInterfacePackageFragmentProvider(storageManager, builtInsModule),
                    (metadataFactories.DefaultDeserializedDescriptorFactory as KlibMetadataModuleDescriptorFactoryImpl)
                        .createForwardDeclarationHackPackagePartProvider(storageManager, builtInsModule)
                ),
                "CompositeProvider@BrsBuiltins for $builtInsModule"
            )
        )

        builtInsModule.setDependencies(listOf(builtInsModule))

        LOG.warn("BRS: BuiltIns module initialized. Checking builtIns: ${builtInsModule.builtIns}")
        try {
            val anyType = builtInsModule.builtIns.anyType
            LOG.warn("BRS: Successfully retrieved anyType: $anyType")
        } catch (e: Exception) {
            LOG.warn("BRS: Failed to retrieve anyType: ${e.message}")
        }

        return builtInsModule.builtIns
    }

    object BrsBuiltInsCacheKey : BuiltInsCacheKey
}

// Use KonanBuiltIns for BRS since it uses the same klib format as Native
private val metadataFactories = KlibMetadataFactories(::KonanBuiltIns, NullFlexibleTypeDeserializer)

private fun ModuleInfo.findBrsStdlib(): BrsKlibLibraryInfo? {
    val allDeps = dependencies().lazyClosure { it.dependencies() }.toList()
    LOG.warn("BRS: findBrsStdlib - searching ${allDeps.size} dependencies")

    val brsLibs = allDeps.filterIsInstance<BrsKlibLibraryInfo>()
    LOG.warn("BRS: findBrsStdlib - found ${brsLibs.size} BrsKlibLibraryInfo instances")

    for (lib in brsLibs) {
        LOG.warn("BRS: BrsKlibLibraryInfo: root=${lib.libraryRoot}, isStdlib=${lib.isStdlib}, compatible=${lib.compatibilityInfo.isCompatible}")
    }

    // Also log all dependency types for debugging
    val depTypes = allDeps.groupBy { it::class.simpleName }.mapValues { it.value.size }
    LOG.warn("BRS: Dependency types: $depTypes")

    return brsLibs.firstOrNull { it.isStdlib && it.compatibilityInfo.isCompatible }
}

/**
 * @see [org.jetbrains.kotlin.utils.closure].
 */
private fun <T> Collection<T>.lazyClosure(f: (T) -> Collection<T>): Sequence<T> = sequence {
    if (isEmpty()) return@sequence
    var sizeBeforeIteration = 0

    yieldAll(this@lazyClosure)
    var yieldedCount = size
    var elementsToCheck = this@lazyClosure

    while (yieldedCount > sizeBeforeIteration) {
        val toAdd = hashSetOf<T>()
        elementsToCheck.forEach {
            val neighbours = f(it)
            yieldAll(neighbours)
            yieldedCount += neighbours.size
            toAdd.addAll(neighbours)
        }
        elementsToCheck = toAdd
        sizeBeforeIteration = yieldedCount
    }
}
