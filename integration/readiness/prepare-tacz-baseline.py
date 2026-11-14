"""Copy only QA fixtures into an isolated 1.20.1 checkout; never edit its mod code."""
import argparse
import re
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("baseline", type=Path, help="Isolated copy of the original 1.20.1 project")
args = parser.parse_args()
source = Path(__file__).resolve().parents[2]
baseline = args.baseline.resolve()
if baseline == source or not (baseline / "forge/build.gradle").is_file():
    parser.error("Supply a separate 1.20.1 checkout containing forge/build.gradle")
fixture = (source / "common/src/tacztest/java/com/timestop/test/NativeTaczScenarios.java").read_text()
fixture = fixture.replace("ResourceLocation.parse(", "new ResourceLocation(")
fixture = fixture.replace("net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(", "new net.minecraft.resources.ResourceLocation(")
fixture = re.sub(r"h.makeMockPlayer\(net.minecraft.world.level.GameType.\w+\)", "h.makeMockPlayer()", fixture)
fixture = fixture.replace("ItemStack.isSameItemSameComponents", "ItemStack.isSameItemSameTags")
fixture = fixture.replace('type.getMethod("build",HolderLookup.Provider.class).invoke(b,h.getLevel().registryAccess())', 'type.getMethod("build").invoke(b)')
fixture = fixture.replace("import net.minecraft.core.HolderLookup;", "")
fixture = fixture.replace(",net.minecraft.server.level.ClientInformation.createDefault()", "")
fixture = fixture.replace(",\n            net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)", "")
fixture = fixture.replace("TaczPrecision.wrapShot(", "wrapShot(")
# The baseline predates the Creative Watch matrix; verify its socketed counterpart.
fixture = fixture.replace('watch=new ItemStack(ModItems.CREATIVE_WATCH.get());AbstractWatchItem.setMatrixRune(watch,com.timestop.item.rune.RuneType.DEFLECTION,true);',
                          'watch=new ItemStack(ModItems.NETHERITE_WATCH.get());AbstractWatchItem.setSocketedRune(watch,new ItemStack(ModItems.RUNE_DEFLECTION.get()));')
fixture = fixture.replace("public class NativeTaczScenarios {", """public class NativeTaczScenarios {
    private static java.util.function.BooleanSupplier wrapShot(java.util.function.BooleanSupplier round, java.util.function.Supplier<Float> pitch, ItemStack gun) {
        return ()->{TaczPrecision.begin(pitch);boolean ok=false;try {ok=round.getAsBoolean();return ok;}finally {TaczPrecision.end(ok,gun);}};
    }
""")
qa = baseline / "common/src/tacztest/java/com/timestop/test"
qa.mkdir(parents=True, exist_ok=True)
(qa / "NativeTaczScenarios.java").write_text(fixture)
shutil.copy2(source / "common/src/tacztest/java/com/timestop/test/NativeTaczEvents.java", qa)
client = (source / "common/src/tacztest/java/com/timestop/test/NativeTaczClientChecks.java").read_text()
client = client.replace('        if (NativeTaczNetworkChecks.active()) { NativeTaczNetworkChecks.tick(); return; }\n', '')
client = client.replace('ResourceLocation.parse(', 'new ResourceLocation(').replace('import net.minecraft.core.HolderLookup;', '')
client = client.replace('.createWorldDimensions(),mc.screen);', '.createWorldDimensions());')
client = re.sub(r'                    try \{gun=.*?catch\(NoSuchMethodException olderPort\)\{gun=.*?\}',
                '                    gun=(ItemStack)builder.getMethod("build").invoke(b);', client, flags=re.S)
(qa / "NativeTaczClientChecks.java").write_text(client)
wrapper = (source / "neoforge/src/tacztest/java/com/timestop/neoforge/test/NativeTaczTests.java").read_text()
wrapper = wrapper.replace("com.timestop.neoforge.test", "com.timestop.forge.test").replace("net.neoforged.neoforge.gametest", "net.minecraftforge.gametest")
target = baseline / "forge/src/tacztest/java/com/timestop/forge/test"
target.mkdir(parents=True, exist_ok=True)
(target / "NativeTaczTests.java").write_text(wrapper)
adapter = (source / "forge/src/tacztest/java/com/timestop/forge/test/NativeTaczClientRegistration.java").read_text()
(target / "NativeTaczClientRegistration.java").write_text(adapter.replace('modid="timestop_tacz_tests"', 'modid="timestop"'))
structure = baseline / "forge/src/tacztest/resources/data/timestop/structures"
structure.mkdir(parents=True, exist_ok=True)
shutil.copy2(source / "forge/src/tacztest/resources/data/timestop/structure/tacz_empty.nbt", structure)
integration = baseline / "integration/readiness"
integration.mkdir(parents=True, exist_ok=True)
shutil.copy2(source / "integration/readiness/tacz-baseline.init.gradle", integration)
shutil.copytree(source / "integration/readiness/tacz-pack", integration / "tacz-pack", dirs_exist_ok=True)
print("Prepared 30 baseline fixtures; production source is unchanged.")
