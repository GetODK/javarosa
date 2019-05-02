package org.javarosa.benchmarks;

import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManagerTestUtils;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.nio.file.Path;

import static org.javarosa.benchmarks.BenchmarkUtils.dryRun;
import static org.javarosa.benchmarks.BenchmarkUtils.prepareAssets;

public class ExternalDataInstanceBuildBenchmark {
    public static void main(String[] args) {
        dryRun(FormDefValidateBenchmark.class);
    }

    @State(Scope.Thread)
    public static class ExternalDataInstanceState {
        @Setup(Level.Trial)
        public void initialize() {
            Path assetsDir = prepareAssets("wards.xml", "lgas.xml");
            ReferenceManagerTestUtils.setUpSimpleReferenceManager("file", assetsDir);
        }
    }

      // //@Benchmark
    public void benchmark_ExternalDataInstance_build_wards(ExternalDataInstanceState state, Blackhole bh)
        throws IOException, XmlPullParserException, InvalidReferenceException,
        UnfullfilledRequirementsException, InvalidStructureException {
        ExternalDataInstance wardsExternalInstance =
            ExternalDataInstance.build("jr://file/wards.xml", "wards");
        bh.consume(wardsExternalInstance);
    }

    //@Benchmark
    public void
    benchmark_ExternalDataInstance_build_lgas(ExternalDataInstanceState state, Blackhole bh)
        throws IOException, XmlPullParserException, InvalidReferenceException,
        UnfullfilledRequirementsException, InvalidStructureException {
        ExternalDataInstance lgaIExternalInstance =
            ExternalDataInstance.build("jr://file/lgas.xml", "lgas");
        bh.consume(lgaIExternalInstance);
    }

}
