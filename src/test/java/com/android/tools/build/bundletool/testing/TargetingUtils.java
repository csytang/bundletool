/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.utils.ProtoUtils.mergeFromProtos;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.DeviceFeature;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.GraphicsApi;
import com.android.bundle.Targeting.GraphicsApiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.bundle.Targeting.OpenGlVersion;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.bundle.Targeting.VulkanVersion;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.utils.Versions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.Int32Value;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Utility functions for creating targeting configurations for tests. */
public final class TargetingUtils {

  // Assets.pb helper methods

  public static Assets assets(TargetedAssetsDirectory... directories) {
    return Assets.newBuilder().addAllDirectory(Lists.newArrayList(directories)).build();
  }

  public static TargetedAssetsDirectory targetedAssetsDirectory(
      String path, AssetsDirectoryTargeting targeting) {
    return TargetedAssetsDirectory.newBuilder().setPath(path).setTargeting(targeting).build();
  }

  public static AssetsDirectoryTargeting mergeAssetsTargeting(
      AssetsDirectoryTargeting targeting, AssetsDirectoryTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  // Assets directory targeting helpers for given targeting dimensions.
  // These should be written in terms of existing targeting proto helpers.
  // See below, for the targeting dimension helper methods.

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(AbiTargeting abiTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setAbi(abiTargeting).build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(String architecture) {
    AbiAlias alias =
        AbiName.fromPlatformName(architecture)
            .orElseThrow(() -> new IllegalArgumentException("Unrecognized ABI: " + architecture))
            .toProto();
    return assetsDirectoryTargeting(abiTargeting(alias));
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      GraphicsApiTargeting graphicsTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setGraphicsApi(graphicsTargeting).build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      TextureCompressionFormatTargeting textureCompressionFormatTargeting) {
    return AssetsDirectoryTargeting.newBuilder()
        .setTextureCompressionFormat(textureCompressionFormatTargeting)
        .build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      LanguageTargeting languageTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setLanguage(languageTargeting).build();
  }

  // Native.pb helper methods.

  public static NativeLibraries nativeLibraries(TargetedNativeDirectory... nativeDirectories) {
    return NativeLibraries.newBuilder()
        .addAllDirectory(Lists.newArrayList(nativeDirectories))
        .build();
  }

  public static TargetedNativeDirectory targetedNativeDirectory(
      String path, NativeDirectoryTargeting targeting) {
    return TargetedNativeDirectory.newBuilder().setPath(path).setTargeting(targeting).build();
  }

  // Native directory targeting helpers for given targeting dimensions.
  // These should be written in terms of existing targeting proto helpers.
  // See below, for the targeting dimension helper methods.

  public static NativeDirectoryTargeting nativeDirectoryTargeting(AbiAlias abi) {
    return NativeDirectoryTargeting.newBuilder().setAbi(Abi.newBuilder().setAlias(abi)).build();
  }

  public static NativeDirectoryTargeting nativeDirectoryTargeting(String architecture) {
    AbiAlias alias =
        AbiName.fromPlatformName(architecture)
            .orElseThrow(() -> new IllegalArgumentException("Unrecognized ABI: " + architecture))
            .toProto();
    return nativeDirectoryTargeting(alias);
  }

  public static NativeDirectoryTargeting nativeDirectoryTargeting(
      TextureCompressionFormatAlias tcf) {
    return NativeDirectoryTargeting.newBuilder()
        .setTextureCompressionFormat(TextureCompressionFormat.newBuilder().setAlias(tcf))
        .build();
  }

  // Apk Targeting helpers. Should be written in terms of existing targeting dimension protos or
  // helpers. See below, for the targeting dimension helper methods.

  public static ApkTargeting mergeApkTargeting(ApkTargeting targeting, ApkTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  public static ApkTargeting apkAbiTargeting(AbiTargeting abiTargeting) {
    return ApkTargeting.newBuilder().setAbiTargeting(abiTargeting).build();
  }

  public static ApkTargeting apkAbiTargeting(
      ImmutableSet<AbiAlias> abiAliases, ImmutableSet<AbiAlias> alternativeAbis) {
    return apkAbiTargeting(abiTargeting(abiAliases, alternativeAbis));
  }

  public static ApkTargeting apkAbiTargeting(
      AbiAlias abiAlias, ImmutableSet<AbiAlias> alternativeAbis) {
    return apkAbiTargeting(abiTargeting(abiAlias, alternativeAbis));
  }

  public static ApkTargeting apkAbiTargeting(AbiAlias abiAlias) {
    return apkAbiTargeting(abiTargeting(abiAlias));
  }

  public static ApkTargeting apkDensityTargeting(ScreenDensityTargeting screenDensityTargeting) {
    return ApkTargeting.newBuilder().setScreenDensityTargeting(screenDensityTargeting).build();
  }

  public static ApkTargeting apkDensityTargeting(
      ImmutableSet<DensityAlias> densities, Set<DensityAlias> alternativeDensities) {
    return apkDensityTargeting(screenDensityTargeting(densities, alternativeDensities));
  }

  public static ApkTargeting apkDensityTargeting(
      DensityAlias density, Set<DensityAlias> alternativeDensities) {
    return apkDensityTargeting(screenDensityTargeting(density, alternativeDensities));
  }

  public static ApkTargeting apkDensityTargeting(DensityAlias density) {
    return apkDensityTargeting(screenDensityTargeting(density));
  }

  public static ApkTargeting apkLanguageTargeting(LanguageTargeting languageTargeting) {
    return ApkTargeting.newBuilder().setLanguageTargeting(languageTargeting).build();
  }

  public static ApkTargeting apkLanguageTargeting(String... languages) {
    return apkLanguageTargeting(languageTargeting(languages));
  }

  public static ApkTargeting apkAlternativeLanguageTargeting(String... alternativeLanguages) {
    return apkLanguageTargeting(alternativeLanguageTargeting(alternativeLanguages));
  }

  public static ApkTargeting apkMinSdkTargeting(int minSdkVersion) {
    return apkSdkTargeting(sdkVersionFrom(minSdkVersion));
  }

  public static ApkTargeting apkGraphicsTargeting(GraphicsApiTargeting graphicsTargeting) {
    return ApkTargeting.newBuilder().setGraphicsApiTargeting(graphicsTargeting).build();
  }

  public static ApkTargeting apkSdkTargeting(SdkVersion sdkVersion) {
    return ApkTargeting.newBuilder()
        .setSdkVersionTargeting(SdkVersionTargeting.newBuilder().addValue(sdkVersion))
        .build();
  }

  public static ApkTargeting apkTextureTargeting(
      TextureCompressionFormatTargeting textureCompressionFormatTargeting) {
    return ApkTargeting.newBuilder()
        .setTextureCompressionFormatTargeting(textureCompressionFormatTargeting)
        .build();
  }

  // Variant Targeting helpers. Should be written in terms of existing targeting dimension protos or
  // helpers. See below, for the targeting dimension helper methods.

  public static VariantTargeting variantAbiTargeting(AbiAlias value) {
    return variantAbiTargeting(value, ImmutableSet.of());
  }

  public static VariantTargeting variantAbiTargeting(
      AbiAlias value, ImmutableSet<AbiAlias> alternatives) {
    return VariantTargeting.newBuilder().setAbiTargeting(abiTargeting(value, alternatives)).build();
  }

  public static VariantTargeting variantAbiTargeting(Abi value) {
    return variantAbiTargeting(value, ImmutableSet.of());
  }

  public static VariantTargeting variantAbiTargeting(Abi value, ImmutableSet<Abi> alternatives) {
    return VariantTargeting.newBuilder()
        .setAbiTargeting(AbiTargeting.newBuilder().addValue(value).addAllAlternatives(alternatives))
        .build();
  }

  public static Abi toAbi(AbiAlias alias) {
    return Abi.newBuilder().setAlias(alias).build();
  }

  public static VariantTargeting variantMinSdkTargeting(
      int minSdkVersion, int... alternativeSdkVersions) {

    return variantSdkTargeting(
        sdkVersionFrom(minSdkVersion),
        Arrays.stream(alternativeSdkVersions)
            .mapToObj(TargetingUtils::sdkVersionFrom)
            .collect(toImmutableSet()));
  }

  public static VariantTargeting variantSdkTargeting(SdkVersion sdkVersion) {
    return variantSdkTargeting(sdkVersion, ImmutableSet.of());
  }

  public static VariantTargeting variantSdkTargeting(
      SdkVersion sdkVersion, ImmutableSet<SdkVersion> alternatives) {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(sdkVersionTargeting(sdkVersion, alternatives))
        .build();
  }

  public static VariantTargeting variantSdkTargeting(int minSdkVersion) {
    return variantSdkTargeting(sdkVersionFrom(minSdkVersion), ImmutableSet.of());
  }

  public static VariantTargeting variantSdkTargeting(
      int minSdkVersion, ImmutableSet<Integer> alternativeMinSdkVersions) {
    return variantSdkTargeting(
        sdkVersionFrom(minSdkVersion),
        alternativeMinSdkVersions
            .stream()
            .map(TargetingUtils::sdkVersionFrom)
            .collect(toImmutableSet()));
  }

  public static VariantTargeting variantDensityTargeting(
      ScreenDensityTargeting screenDensityTargeting) {
    return VariantTargeting.newBuilder().setScreenDensityTargeting(screenDensityTargeting).build();
  }

  public static VariantTargeting variantDensityTargeting(DensityAlias value) {
    return variantDensityTargeting(screenDensityTargeting(value));
  }

  public static VariantTargeting variantDensityTargeting(
      ImmutableSet<DensityAlias> densities, ImmutableSet<DensityAlias> alternativeDensities) {
    return variantDensityTargeting(screenDensityTargeting(densities, alternativeDensities));
  }

  public static VariantTargeting variantDensityTargeting(
      DensityAlias density, ImmutableSet<DensityAlias> alternativeDensities) {
    return variantDensityTargeting(screenDensityTargeting(density, alternativeDensities));
  }

  public static VariantTargeting variantDensityTargeting(ScreenDensity value) {
    return variantDensityTargeting(value, ImmutableSet.of());
  }

  public static VariantTargeting variantDensityTargeting(
      ScreenDensity value, ImmutableSet<ScreenDensity> alternatives) {
    return VariantTargeting.newBuilder()
        .setScreenDensityTargeting(
            ScreenDensityTargeting.newBuilder().addValue(value).addAllAlternatives(alternatives))
        .build();
  }

  public static VariantTargeting mergeVariantTargeting(
      VariantTargeting targeting, VariantTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  // Module Targeting helpers.

  public static ModuleTargeting moduleFeatureTargeting(String featureName) {
    return ModuleTargeting.newBuilder()
        .addDeviceFeatureTargeting(deviceFeatureTargeting(featureName))
        .build();
  }

  public static ModuleTargeting moduleFeatureTargeting(String featureName, int featureVersion) {
    return ModuleTargeting.newBuilder()
        .addDeviceFeatureTargeting(deviceFeatureTargeting(featureName, featureVersion))
        .build();
  }

  public static ModuleTargeting moduleMinSdkVersionTargeting(int minSdkVersion) {
    return ModuleTargeting.newBuilder()
        .setSdkVersionTargeting(sdkVersionTargeting(sdkVersionFrom(minSdkVersion)))
        .build();
  }

  public static ModuleTargeting mergeModuleTargeting(
      ModuleTargeting targeting, ModuleTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  // Per-dimension targeting helper methods.

  // ABI targeting.

  public static AbiTargeting abiTargeting(AbiAlias abi) {
    return abiTargeting(ImmutableSet.of(abi), ImmutableSet.of());
  }

  public static AbiTargeting abiTargeting(AbiAlias abi, ImmutableSet<AbiAlias> alternatives) {
    return abiTargeting(ImmutableSet.of(abi), alternatives);
  }

  public static AbiTargeting abiTargeting(
      ImmutableSet<AbiAlias> abiAliases, ImmutableSet<AbiAlias> alternatives) {
    return AbiTargeting.newBuilder()
        .addAllValue(
            abiAliases
                .stream()
                .map(alias -> Abi.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .addAllAlternatives(
            alternatives
                .stream()
                .map(alias -> Abi.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .build();
  }

  // Graphics API Targeting

  public static GraphicsApi openGlVersionFrom(int fromMajor) {
    return openGlVersionFrom(fromMajor, 0);
  }

  public static GraphicsApi openGlVersionFrom(int fromMajor, int fromMinor) {
    return GraphicsApi.newBuilder()
        .setMinOpenGlVersion(OpenGlVersion.newBuilder().setMajor(fromMajor).setMinor(fromMinor))
        .build();
  }

  public static GraphicsApiTargeting graphicsApiTargeting(
      ImmutableSet<GraphicsApi> values, ImmutableSet<GraphicsApi> alternatives) {
    return GraphicsApiTargeting.newBuilder()
        .addAllValue(values)
        .addAllAlternatives(alternatives)
        .build();
  }

  public static GraphicsApiTargeting graphicsApiTargeting(GraphicsApi value) {
    return graphicsApiTargeting(ImmutableSet.of(value), ImmutableSet.of());
  }

  public static GraphicsApiTargeting openGlVersionTargeting(
      GraphicsApi value, ImmutableSet<GraphicsApi> alternatives) {
    return graphicsApiTargeting(ImmutableSet.of(value), alternatives);
  }

  public static GraphicsApi vulkanVersionFrom(int fromMajor) {
    return vulkanVersionFrom(fromMajor, 0);
  }

  public static GraphicsApi vulkanVersionFrom(int fromMajor, int fromMinor) {
    return GraphicsApi.newBuilder()
        .setMinVulkanVersion(VulkanVersion.newBuilder().setMajor(fromMajor).setMinor(fromMinor))
        .build();
  }

  // Screen Density targeting.

  public static ScreenDensityTargeting screenDensityTargeting(
      ImmutableSet<DensityAlias> densities, Set<DensityAlias> alternativeDensities) {
    return ScreenDensityTargeting.newBuilder()
        .addAllValue(
            densities.stream().map(TargetingUtils::toScreenDensity).collect(toImmutableList()))
        .addAllAlternatives(
            alternativeDensities
                .stream()
                .map(TargetingUtils::toScreenDensity)
                .collect(toImmutableList()))
        .build();
  }

  public static ScreenDensityTargeting screenDensityTargeting(
      int densityDpiValue, ImmutableSet<DensityAlias> alternativeDensities) {
    return ScreenDensityTargeting.newBuilder()
        .addValue(toScreenDensity(densityDpiValue))
        .addAllAlternatives(
            alternativeDensities
                .stream()
                .map(TargetingUtils::toScreenDensity)
                .collect(toImmutableList()))
        .build();
  }

  public static ScreenDensity toScreenDensity(DensityAlias densityAlias) {
    return ScreenDensity.newBuilder().setDensityAlias(densityAlias).build();
  }

  public static ScreenDensity toScreenDensity(int densityDpi) {
    return ScreenDensity.newBuilder().setDensityDpi(densityDpi).build();
  }

  public static ScreenDensityTargeting screenDensityTargeting(
      DensityAlias density, Set<DensityAlias> alternativeDensities) {
    return screenDensityTargeting(ImmutableSet.of(density), alternativeDensities);
  }

  public static ScreenDensityTargeting screenDensityTargeting(DensityAlias density) {
    return screenDensityTargeting(ImmutableSet.of(density), ImmutableSet.of());
  }

  // Language targeting.

  /**
   * Deliberately private, because bundletool should never produce language targeting with both
   * `values` and `alternatives`.
   */
  private static LanguageTargeting languageTargeting(
      ImmutableSet<String> languages, ImmutableSet<String> alternativeLanguages) {
    return LanguageTargeting.newBuilder()
        .addAllValue(languages)
        .addAllAlternatives(alternativeLanguages)
        .build();
  }

  /**
   * This method should be used only in highly-specialized tests.
   *
   * @deprecated Bundletool never produces language targeting with both `values` and `alternatives`
   *     set.
   */
  @Deprecated
  public static LanguageTargeting languageTargeting(
      String language, ImmutableSet<String> alternativeLanguages) {
    return languageTargeting(ImmutableSet.of(language), alternativeLanguages);
  }

  public static LanguageTargeting languageTargeting(String... languages) {
    return languageTargeting(ImmutableSet.copyOf(languages), ImmutableSet.of());
  }

  public static LanguageTargeting alternativeLanguageTargeting(String... alternativeLanguages) {
    return languageTargeting(ImmutableSet.of(), ImmutableSet.copyOf(alternativeLanguages));
  }

  // SDK Version targeting.

  public static SdkVersion sdkVersionFrom(int from) {
    return SdkVersion.newBuilder().setMin(Int32Value.of(from)).build();
  }

  public static SdkVersionTargeting sdkVersionTargeting(
      SdkVersion sdkVersion, ImmutableSet<SdkVersion> alternatives) {
    return SdkVersionTargeting.newBuilder()
        .addValue(sdkVersion)
        .addAllAlternatives(alternatives)
        .build();
  }

  public static SdkVersionTargeting sdkVersionTargeting(SdkVersion sdkVersion) {
    return SdkVersionTargeting.newBuilder().addValue(sdkVersion).build();
  }

  // Texture Compression Format targeting.

  public static TextureCompressionFormat textureCompressionFormat(
      TextureCompressionFormatAlias alias) {
    return TextureCompressionFormat.newBuilder().setAlias(alias).build();
  }

  public static TextureCompressionFormatTargeting textureCompressionTargeting(
      ImmutableSet<TextureCompressionFormatAlias> values,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return TextureCompressionFormatTargeting.newBuilder()
        .addAllValue(
            values
                .stream()
                .map(alias -> TextureCompressionFormat.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .addAllAlternatives(
            alternatives
                .stream()
                .map(alias -> TextureCompressionFormat.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .build();
  }

  public static TextureCompressionFormatTargeting textureCompressionTargeting(
      TextureCompressionFormatAlias value) {
    return textureCompressionTargeting(ImmutableSet.of(value), ImmutableSet.of());
  }

  public static TextureCompressionFormatTargeting textureCompressionTargeting(
      TextureCompressionFormatAlias value,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return textureCompressionTargeting(ImmutableSet.of(value), alternatives);
  }

  // Device Feature targeting.

  public static DeviceFeatureTargeting deviceFeatureTargeting(String featureName) {
    return DeviceFeatureTargeting.newBuilder()
        .setRequiredFeature(DeviceFeature.newBuilder().setFeatureName(featureName))
        .build();
  }

  public static DeviceFeatureTargeting deviceFeatureTargeting(
      String featureName, int featureVersion) {
    return DeviceFeatureTargeting.newBuilder()
        .setRequiredFeature(
            DeviceFeature.newBuilder()
                .setFeatureName(featureName)
                .setFeatureVersion(featureVersion))
        .build();
  }

  public static ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetingList(
      String... featureNames) {
    return Arrays.asList(featureNames).stream()
        .map(TargetingUtils::deviceFeatureTargeting)
        .collect(toImmutableList());
  }

  // Helper methods for processing splits.

  public static ImmutableList<ModuleSplit> filterSplitsByTargeting(
      Collection<ModuleSplit> splits,
      Predicate<ApkTargeting> apkTargetingPredicate,
      Predicate<VariantTargeting> variantTargetingPredicate) {
    return splits
        .stream()
        .filter(moduleSplit -> apkTargetingPredicate.test(moduleSplit.getApkTargeting()))
        .filter(moduleSplit -> variantTargetingPredicate.test(moduleSplit.getVariantTargeting()))
        .collect(toImmutableList());
  }

  public static ImmutableList<ModuleSplit> getSplitsWithTargetingEqualTo(
      Collection<ModuleSplit> splits, ApkTargeting apkTargeting) {
    return filterSplitsByTargeting(
        splits,
        apkTargeting::equals,
        variantTargeting -> variantTargeting.equals(lPlusVariantTargeting()));
  }

  public static ImmutableList<ModuleSplit> getSplitsWithDefaultTargeting(
      Collection<ModuleSplit> splits) {
    return getSplitsWithTargetingEqualTo(splits, ApkTargeting.getDefaultInstance());
  }

  public static void assertForSingleDefaultSplit(
      Collection<ModuleSplit> splits, Consumer<ModuleSplit> assertionFn) {
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(splits);
    assertThat(defaultSplits).hasSize(1);
    assertionFn.accept(defaultSplits.get(0));
  }

  public static void assertForNonDefaultSplits(
      Collection<ModuleSplit> splits, Consumer<ModuleSplit> assertionFn) {
    ImmutableList<ModuleSplit> nonDefaultSplits =
        filterSplitsByTargeting(
            splits,
            Predicates.not(ApkTargeting.getDefaultInstance()::equals),
            Predicates.alwaysTrue());
    assertThat(nonDefaultSplits).isNotEmpty();
    nonDefaultSplits.stream().forEach(assertionFn);
  }

  public static VariantTargeting lPlusVariantTargeting() {
    return variantMinSdkTargeting(Versions.ANDROID_L_API_VERSION);
  }

  // Not meant to be instantiated.
  private TargetingUtils() {}
}
