/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.build.bundletool.targeting;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.mergeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toAbi;
import static com.android.tools.build.bundletool.testing.TargetingUtils.toScreenDensity;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantAbiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantSdkTargeting;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.targeting.AlternativeVariantTargetingPopulator.AbiAlternativesPopulator;
import com.android.tools.build.bundletool.targeting.AlternativeVariantTargetingPopulator.ScreenDensityAlternativesPopulator;
import com.android.tools.build.bundletool.targeting.AlternativeVariantTargetingPopulator.SdkVersionAlternativesPopulator;
import com.android.tools.build.bundletool.utils.Versions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AlternativeVariantTargetingPopulatorTest {

  @Test
  public void splitAndStandalones_addsAlternatives() throws Exception {
    SdkVersion lPlusVersion = sdkVersionFrom(Versions.ANDROID_L_API_VERSION);
    VariantTargeting lPlusTargeting = variantSdkTargeting(lPlusVersion);
    VariantTargeting emptySdkTargeting = variantSdkTargeting(SdkVersion.getDefaultInstance());

    // Post-L splits with 2 modules.
    ImmutableList<ModuleSplit> postLSplits =
        ImmutableList.of(createModuleSplit(lPlusTargeting), createModuleSplit(lPlusTargeting));
    // 3 density shards.
    ImmutableList<ModuleSplit> standaloneShards =
        ImmutableList.of(
            createStandaloneModuleSplit(
                mergeVariantTargeting(
                    emptySdkTargeting, variantDensityTargeting(DensityAlias.MDPI))),
            createStandaloneModuleSplit(
                mergeVariantTargeting(
                    emptySdkTargeting, variantDensityTargeting(DensityAlias.HDPI))),
            createStandaloneModuleSplit(
                mergeVariantTargeting(
                    emptySdkTargeting, variantDensityTargeting(DensityAlias.XHDPI))));
    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setStandaloneApks(standaloneShards)
            .setSplitApks(postLSplits)
            .build();

    GeneratedApks processedApks =
        AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(generatedApks);

    assertThat(processedApks.size()).isEqualTo(5);
    ImmutableCollection<ModuleSplit> processedShards = processedApks.getStandaloneApks();
    assertThat(processedShards).hasSize(3);
    assertThat(
            processedShards
                .stream()
                .map(ModuleSplit::getVariantTargeting)
                .collect(toImmutableSet()))
        .containsExactly(
            mergeVariantTargeting(
                variantSdkTargeting(SdkVersion.getDefaultInstance(), ImmutableSet.of(lPlusVersion)),
                variantDensityTargeting(
                    DensityAlias.MDPI, ImmutableSet.of(DensityAlias.HDPI, DensityAlias.XHDPI))),
            mergeVariantTargeting(
                variantSdkTargeting(SdkVersion.getDefaultInstance(), ImmutableSet.of(lPlusVersion)),
                variantDensityTargeting(
                    DensityAlias.HDPI, ImmutableSet.of(DensityAlias.MDPI, DensityAlias.XHDPI))),
            mergeVariantTargeting(
                variantSdkTargeting(SdkVersion.getDefaultInstance(), ImmutableSet.of(lPlusVersion)),
                variantDensityTargeting(
                    DensityAlias.XHDPI, ImmutableSet.of(DensityAlias.MDPI, DensityAlias.HDPI))));
  }

  @Test
  public void instantPassThrough() throws Exception {
    SdkVersion lPlusVersion = sdkVersionFrom(Versions.ANDROID_L_API_VERSION);
    VariantTargeting lPlusTargeting = variantSdkTargeting(lPlusVersion);
    VariantTargeting emptySdkTargeting = variantSdkTargeting(SdkVersion.getDefaultInstance());

    // Post-L splits with 1 module.
    ImmutableList<ModuleSplit> postLSplits = ImmutableList.of(createModuleSplit(lPlusTargeting));
    // Post-L instant splits with 2 modules
    ImmutableList<ModuleSplit> instantSplits =
        ImmutableList.of(
            createModuleSplit(lPlusTargeting, SplitType.INSTANT),
            createModuleSplit(lPlusTargeting, SplitType.INSTANT));
    // 1 density shard.
    ImmutableList<ModuleSplit> standaloneShards =
        ImmutableList.of(
            createStandaloneModuleSplit(
                mergeVariantTargeting(
                    emptySdkTargeting, variantDensityTargeting(DensityAlias.XHDPI))));
    GeneratedApks generatedApks =
        GeneratedApks.builder()
            .setStandaloneApks(standaloneShards)
            .setInstantApks(instantSplits)
            .setSplitApks(postLSplits)
            .build();

    GeneratedApks processedApks =
        AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(generatedApks);

    assertThat(processedApks.size()).isEqualTo(4);
    ImmutableCollection<ModuleSplit> processedSplits = processedApks.getSplitApks();
    assertThat(processedSplits).hasSize(1);
    ImmutableCollection<ModuleSplit> processedShards = processedApks.getStandaloneApks();
    assertThat(processedShards).hasSize(1);
    ImmutableCollection<ModuleSplit> processedInstantSplits = processedApks.getInstantApks();
    assertThat(processedInstantSplits).isEqualTo(instantSplits);
  }

  @Test
  public void abi_allVariantsAbiAgnostic_passThrough() throws Exception {
    ModuleSplit densityVariant = createModuleSplit(variantDensityTargeting(DensityAlias.LDPI));
    ModuleSplit sdkVariant = createModuleSplit(variantSdkTargeting(sdkVersionFrom(1)));
    ImmutableList<ModuleSplit> originalSplits = ImmutableList.of(densityVariant, sdkVariant);

    ImmutableList<ModuleSplit> outputVariants =
        new AbiAlternativesPopulator().addAlternativeVariantTargeting(originalSplits);

    assertThat(originalSplits).isEqualTo(outputVariants);
  }

  @Test
  public void abi_oneVariantAbiAgnostic_throws() throws Exception {
    VariantTargeting x86Targeting = variantAbiTargeting(AbiAlias.X86);
    VariantTargeting nonAbiTargeting = variantSdkTargeting(sdkVersionFrom(21));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AbiAlternativesPopulator()
                    .addAlternativeVariantTargeting(
                        ImmutableList.of(
                            createModuleSplit(x86Targeting), createModuleSplit(nonAbiTargeting))));

    assertThat(exception)
        .hasMessageThat()
        .contains("Some variants are agnostic to the dimension, and some are not");
  }

  @Test
  public void abi_alternativesPopulated() throws Exception {
    VariantTargeting x86Targeting = variantAbiTargeting(AbiAlias.X86);
    VariantTargeting x64Targeting = variantAbiTargeting(AbiAlias.X86_64);
    VariantTargeting defaultAbiTargeting = variantAbiTargeting(Abi.getDefaultInstance());

    ImmutableList<ModuleSplit> outputSplits =
        new AbiAlternativesPopulator()
            .addAlternativeVariantTargeting(
                ImmutableList.of(
                    createModuleSplit(x86Targeting),
                    createModuleSplit(x64Targeting),
                    createModuleSplit(defaultAbiTargeting)));

    assertThat(outputSplits).hasSize(3);
    ModuleSplit x86VariantNew = outputSplits.get(0);
    assertThat(x86VariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantAbiTargeting(
                toAbi(AbiAlias.X86),
                ImmutableSet.of(Abi.getDefaultInstance(), toAbi(AbiAlias.X86_64))));
    ModuleSplit x64VariantNew = outputSplits.get(1);
    assertThat(x64VariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantAbiTargeting(
                toAbi(AbiAlias.X86_64),
                ImmutableSet.of(Abi.getDefaultInstance(), toAbi(AbiAlias.X86))));
    ModuleSplit defaultAbiVariantNew = outputSplits.get(2);
    assertThat(defaultAbiVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantAbiTargeting(
                Abi.getDefaultInstance(),
                ImmutableSet.of(toAbi(AbiAlias.X86), toAbi(AbiAlias.X86_64))));
  }

  @Test
  public void screenDensity_allVariantsAbiAgnostic_passThrough() throws Exception {
    ModuleSplit abiSplit = createModuleSplit(variantAbiTargeting(AbiAlias.X86));
    ModuleSplit sdkSplit = createModuleSplit(variantSdkTargeting(sdkVersionFrom(1)));
    ImmutableList<ModuleSplit> originalVariants = ImmutableList.of(abiSplit, sdkSplit);

    ImmutableList<ModuleSplit> outputVariants =
        new ScreenDensityAlternativesPopulator().addAlternativeVariantTargeting(originalVariants);

    assertThat(originalVariants).isEqualTo(outputVariants);
  }

  @Test
  public void screenDensity_oneVariantDensityAgnostic_throws() throws Exception {
    VariantTargeting ldpiTargeting = variantDensityTargeting(DensityAlias.LDPI);
    VariantTargeting nonDensityTargeting = variantSdkTargeting(sdkVersionFrom(21));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ScreenDensityAlternativesPopulator()
                    .addAlternativeVariantTargeting(
                        ImmutableList.of(
                            createModuleSplit(ldpiTargeting),
                            createModuleSplit(nonDensityTargeting))));

    assertThat(exception)
        .hasMessageThat()
        .contains("Some variants are agnostic to the dimension, and some are not");
  }

  @Test
  public void screenDensity_alternativesPopulated() throws Exception {
    VariantTargeting ldpiTargeting = variantDensityTargeting(DensityAlias.LDPI);
    VariantTargeting mdpiTargeting = variantDensityTargeting(DensityAlias.MDPI);
    VariantTargeting defaultDensityTargeting =
        variantDensityTargeting(ScreenDensity.getDefaultInstance());

    ImmutableList<ModuleSplit> outputVariants =
        new ScreenDensityAlternativesPopulator()
            .addAlternativeVariantTargeting(
                ImmutableList.of(
                    createModuleSplit(ldpiTargeting),
                    createModuleSplit(mdpiTargeting),
                    createModuleSplit(defaultDensityTargeting)));

    assertThat(outputVariants).hasSize(3);
    ModuleSplit ldpiVariantNew = outputVariants.get(0);
    assertThat(ldpiVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantDensityTargeting(
                toScreenDensity(DensityAlias.LDPI),
                ImmutableSet.of(
                    ScreenDensity.getDefaultInstance(), toScreenDensity(DensityAlias.MDPI))));
    ModuleSplit mdpiVariantNew = outputVariants.get(1);
    assertThat(mdpiVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantDensityTargeting(
                toScreenDensity(DensityAlias.MDPI),
                ImmutableSet.of(
                    ScreenDensity.getDefaultInstance(), toScreenDensity(DensityAlias.LDPI))));
    ModuleSplit defaultVariantNew = outputVariants.get(2);
    assertThat(defaultVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantDensityTargeting(
                ScreenDensity.getDefaultInstance(),
                ImmutableSet.of(
                    toScreenDensity(DensityAlias.LDPI), toScreenDensity(DensityAlias.MDPI))));
  }

  @Test
  public void sdk_allVariantsSdkAgnostic_passThrough() throws Exception {
    ModuleSplit abiSplit = createModuleSplit(variantAbiTargeting(AbiAlias.X86));
    ModuleSplit densitySplit = createModuleSplit(variantDensityTargeting(DensityAlias.LDPI));
    ImmutableList<ModuleSplit> originalVariants = ImmutableList.of(abiSplit, densitySplit);

    ImmutableList<ModuleSplit> outputVariants =
        new SdkVersionAlternativesPopulator().addAlternativeVariantTargeting(originalVariants);

    assertThat(originalVariants).isEqualTo(outputVariants);
  }

  @Test
  public void sdk_oneVariantSdkAgnostic_throws() throws Exception {
    VariantTargeting lPlusTargeting = variantSdkTargeting(sdkVersionFrom(21));
    VariantTargeting nonSdkTargeting = variantAbiTargeting(AbiAlias.X86);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SdkVersionAlternativesPopulator()
                    .addAlternativeVariantTargeting(
                        ImmutableList.of(
                            createModuleSplit(lPlusTargeting),
                            createModuleSplit(nonSdkTargeting))));

    assertThat(exception)
        .hasMessageThat()
        .contains("Some variants are agnostic to the dimension, and some are not");
  }

  @Test
  public void sdk_alternativesPopulated() throws Exception {
    VariantTargeting lPlusTargeting = variantSdkTargeting(sdkVersionFrom(21));
    VariantTargeting mPlusTargeting = variantSdkTargeting(sdkVersionFrom(23));
    VariantTargeting defaultSdkTargeting = variantSdkTargeting(SdkVersion.getDefaultInstance());

    ImmutableList<ModuleSplit> outputVariants =
        new SdkVersionAlternativesPopulator()
            .addAlternativeVariantTargeting(
                ImmutableList.of(
                    createModuleSplit(lPlusTargeting),
                    createModuleSplit(mPlusTargeting),
                    createModuleSplit(defaultSdkTargeting)));

    assertThat(outputVariants).hasSize(3);
    ModuleSplit lPlusVariantNew = outputVariants.get(0);
    assertThat(lPlusVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantSdkTargeting(
                sdkVersionFrom(21),
                ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(23))));
    ModuleSplit mPlusVariantNew = outputVariants.get(1);
    assertThat(mPlusVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantSdkTargeting(
                sdkVersionFrom(23),
                ImmutableSet.of(SdkVersion.getDefaultInstance(), sdkVersionFrom(21))));
    ModuleSplit defaultSdkVariantNew = outputVariants.get(2);
    assertThat(defaultSdkVariantNew.getVariantTargeting())
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            variantSdkTargeting(
                SdkVersion.getDefaultInstance(),
                ImmutableSet.of(sdkVersionFrom(21), sdkVersionFrom(23))));
  }

  private static ModuleSplit createStandaloneModuleSplit(VariantTargeting variantTargeting) {
    return createModuleSplit(variantTargeting, SplitType.STANDALONE);
  }

  private static ModuleSplit createModuleSplit(VariantTargeting variantTargeting) {
    return createModuleSplit(variantTargeting, SplitType.SPLIT);
  }

  private static ModuleSplit createModuleSplit(
      VariantTargeting variantTargeting, SplitType splitType) {
    return ModuleSplit.builder()
        .setAndroidManifest(AndroidManifest.create(androidManifest("com.test.app")))
        .setEntries(ImmutableList.of())
        .setMasterSplit(true)
        .setSplitType(splitType)
        .setModuleName(BundleModuleName.create("base"))
        .setApkTargeting(ApkTargeting.getDefaultInstance())
        .setVariantTargeting(variantTargeting)
        .build();
  }
}
