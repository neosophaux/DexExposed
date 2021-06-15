package kilo.dex.exposed;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.dexbacked.DexBackedDexFile.NotADexFile;
import org.jf.dexlib2.dexbacked.ZipDexContainer;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.iface.MultiDexContainer.DexEntry;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.debug.DebugItem;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.dexlib2.immutable.ImmutableField;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.util.DexUtil;
import org.jf.dexlib2.util.DexUtil.InvalidFile;
import org.jf.dexlib2.util.DexUtil.UnsupportedFile;

import javax.annotation.Nonnull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class DexExposed implements Runnable {
    public static final Opcodes SUPPORTED_OPCODES;
    public static final String BIN_DIR;

    // this should probably be updated more.
    private static final List<String> RESERVED_NAMES;

    static {
        SUPPORTED_OPCODES = Opcodes.getDefault();
        BIN_DIR = new File(DexExposed.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .getPath()).getParent();

        RESERVED_NAMES = Arrays.asList(new String[] {
            "<init>", "<clinit>"
        });
    }

    @Parameter(description = "INPUT FILE(S)")
    private List<String> inputFiles = new ArrayList<>();

    @Parameter(names = {"-o", "--output-dir"}, description = "Directory to output to.")
    private String outputDir = BIN_DIR + File.separator + "output";

    @Parameter(names = {"-p", "--do-pkg"}, description = "Only run the tool on a specific package. May be a regex that matches the BINARY version of a package. For example, 'Lex/pkg/[a-f0-9]{32}/data'.")
    private String pkgFilter = ".*";

    @Parameter(names = {"-h", "--help"}, description = "Prints the usage information.")
    private boolean help = false;

    private DexExposed() {}

    public void start(final JCommander commander) throws IOException {
        if(this.inputFiles.size() < 1) {
            commander.usage();

            System.exit(1);
        }

        if(this.help) {
            commander.usage();

            System.exit(0);
        }

        for(final String file : this.inputFiles) {
            final File inputFile = new File(file);
            final ZipDexContainer zipF = new ZipDexContainer(inputFile, null);

            if(! zipF.isZipFile() && ! this.isADexFile(inputFile)) {
                log("[ - ] Skipping invalid file '%s'...", true, file);

                this.inputFiles.remove(file);
            }
        }

        if(this.inputFiles.size() == 0) {
            log("[ - ] No input files. Exiting...", true);

            System.exit(1);
        }

        // all is good now. run the actual app.
        this.run();
    }

    // a replica of org.jf.dexlib2.dexbacked.ZipDexContainer.isDex
    // with some minor convenient differences.
    private boolean isADexFile(final File file) throws IOException {
        InputStream inputStream = null;

        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));

            DexUtil.verifyDexHeader(inputStream);
        } catch(final FileNotFoundException ex) {
            return false;
        } catch(final NotADexFile ex) {
            return false;
        } catch(final InvalidFile ex) {
            return false;
        } catch(final UnsupportedFile ex) {
            return false;
        } finally {
            if(inputStream != null) {
                inputStream.close();
            }
        }

        return true;
    }

    private String getMarkedName(final int dexIdx) {
        if(dexIdx > 1) {
            return "classes" + dexIdx + "_modified.dex";
        }

        return "classes_modified.dex";
    }

    private String getClassPkg(final ClassDef klass) {
        final String type = klass.getType();

        if(type.indexOf('/') == -1) {
            return "";
        }

        final String classPkg = type.substring(0, type.lastIndexOf('/'));

        return classPkg;
    }

    private int expose(int flags) {
        if (AccessFlags.PRIVATE.isSet(flags)) {
            flags &= ~AccessFlags.PRIVATE.getValue();
        } else if (AccessFlags.PROTECTED.isSet(flags)) {
            flags &= ~AccessFlags.PROTECTED.getValue();
        }

        flags |= AccessFlags.PUBLIC.getValue();

        return flags;
    }

    private Iterable<Field> exposeFields(final ClassDef klass) {
        final List<Field> exposedFields = new ArrayList<>();

        for(final Field field : klass.getFields()) {
            exposedFields.add(new ImmutableField(
                field.getDefiningClass(),
                field.getName(),
                field.getType(),
                this.expose(field.getAccessFlags()),
                field.getInitialValue(),
                field.getAnnotations(),
                field.getHiddenApiRestrictions()
            ));
        }

        return exposedFields;
    }

    private boolean isReservedName(final String name) {
        return RESERVED_NAMES.contains(name);
    }

    private Iterable<Method> exposeMethods(final ClassDef klass) {
        final List<Method> exposedMethods = new ArrayList<>();

        for(final Method method : klass.getMethods()) {
            exposedMethods.add(new ImmutableMethod(
                method.getDefiningClass(),
                method.getName(),
                method.getParameters(),
                method.getReturnType(),
                this.expose(method.getAccessFlags()),
                method.getAnnotations(),
                method.getHiddenApiRestrictions(),
                this.fixMethodImpl(method.getImplementation())
            ));
        }

        return exposedMethods;
    }

    private MethodImplementation fixMethodImpl(final MethodImplementation impl) {
        if(impl == null) {
            return null;
        }

        return new MethodImplementation() {
            @Override
            public int getRegisterCount() {
                return impl.getRegisterCount();
            }

            @Nonnull
            @Override
            public Iterable<? extends Instruction> getInstructions() {
                final List<Instruction> exposed = new ArrayList<>();

                for (Instruction instruction : impl.getInstructions()) {
                    if (instruction instanceof Instruction35c) {
                        final Instruction35c instruction35c = (Instruction35c) instruction;

                        if (instruction35c.getReferenceType() == ReferenceType.METHOD) {
                            final MethodReference reference = (MethodReference) instruction35c.getReference();

                            if (! DexExposed.this.isReservedName(reference.getName()) &&
                                    instruction35c.getOpcode() == Opcode.INVOKE_DIRECT) {
                                instruction = new ImmutableInstruction35c(
                                    Opcode.INVOKE_VIRTUAL,
                                    instruction35c.getRegisterCount(),
                                    instruction35c.getRegisterC(),
                                    instruction35c.getRegisterD(),
                                    instruction35c.getRegisterE(),
                                    instruction35c.getRegisterF(),
                                    instruction35c.getRegisterG(),
                                    new ImmutableMethodReference(
                                        reference.getDefiningClass(),
                                        reference.getName(),
                                        reference.getParameterTypes(),
                                        reference.getReturnType()
                                    )
                                );
                            }
                        }
                    } else if (instruction instanceof Instruction3rc) {
                        final Instruction3rc instruction3rc = (Instruction3rc) instruction;

                        if (instruction3rc.getReferenceType() == ReferenceType.METHOD) {
                            final MethodReference reference = (MethodReference) instruction3rc.getReference();

                            if (! DexExposed.this.isReservedName(reference.getName()) &&
                                    instruction3rc.getOpcode() == Opcode.INVOKE_DIRECT_RANGE) {
                                instruction = new ImmutableInstruction3rc(
                                    Opcode.INVOKE_VIRTUAL_RANGE,
                                    instruction3rc.getStartRegister(),
                                    instruction3rc.getRegisterCount(),
                                    new ImmutableMethodReference(
                                        reference.getDefiningClass(),
                                        reference.getName(),
                                        reference.getParameterTypes(),
                                        reference.getReturnType()
                                    )
                                );
                            }
                        }
                    }

                    exposed.add(instruction);
                }

                return exposed;
            }

            @Nonnull
            @Override
            public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks() {
                return impl.getTryBlocks();
            }

            @Nonnull
            @Override
            public Iterable<? extends DebugItem> getDebugItems() {
                return impl.getDebugItems();
            }
        };
    }

    private DexFile processDex(final DexFile theDex) {
        final List<ClassDef> exposedClasses = new ArrayList<>(theDex.getClasses().size());
        final Pattern typeFilter = Pattern.compile(this.pkgFilter);

        for(final ClassDef klass : theDex.getClasses()) {
            final Matcher matcher = typeFilter.matcher(this.getClassPkg(klass));

            if(! matcher.matches() && ! klass.getType().startsWith(this.pkgFilter)) {
                exposedClasses.add(klass);

                continue;
            }

            exposedClasses.add(new ImmutableClassDef(
                klass.getType(),
                this.expose(klass.getAccessFlags()),
                klass.getSuperclass(),
                klass.getInterfaces(),
                klass.getSourceFile(),
                klass.getAnnotations(),
                this.exposeFields(klass),
                this.exposeMethods(klass)
            ));
        }

        return new ImmutableDexFile(
            SUPPORTED_OPCODES,
            exposedClasses
        );
    }

    @Override
    public void run() {
        try {
            for(final String file : this.inputFiles) {
                final ZipDexContainer zipF = new ZipDexContainer(new File(file), SUPPORTED_OPCODES);
                final List<DexFile> toProcess = new ArrayList<>();

                log("[ + ] Processing '%s'...", false, file);

                if(zipF.isZipFile()) {
                    for(final String dexEntry : zipF.getDexEntryNames()) {
                        final DexEntry classesDex = zipF.getEntry(dexEntry);

                        toProcess.add(classesDex.getDexFile());
                    }
                } else {
                    toProcess.add(DexFileFactory.loadDexFile(file, SUPPORTED_OPCODES));
                }

                for(int dexIdx = 0; dexIdx < toProcess.size(); dexIdx++) {
                    final DexFile exposedDex = this.processDex(toProcess.get(dexIdx));
                    final String inputFN = new File(file).getName();
                    final File outputFP = new File(
                        this.outputDir +
                        File.separator +
                        inputFN.substring(0, inputFN.lastIndexOf('.'))
                    );

                    outputFP.mkdirs();

                    log("[ + ] Writing dex to '%s'...", false, outputFP.getAbsolutePath());

                    DexFileFactory.writeDexFile((
                        outputFP.getAbsolutePath() +
                        File.separator +
                        this.getMarkedName(dexIdx)
                    ), exposedDex);
                }
            }
        } catch(final IOException ex) {
            log("[ - ] System error: %s", true, ex.getMessage());

            ex.printStackTrace();

            System.exit(1);
        }
    }

    public static void log(final String msg, final boolean error, final Object... args) {
        final PrintStream logStream = (error ? System.err : System.out);

        logStream.println(String.format(msg, args));
    }

    public static void main(String[] args) throws IOException {
        final DexExposed mcObj = new DexExposed();
        final JCommander jcObj = JCommander.newBuilder()
            .addObject(mcObj)
            .programName("java -jar dxp.jar")
            .build();

        jcObj.parse(args);

        mcObj.start(jcObj);
    }
}
