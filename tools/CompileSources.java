import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.tools.*;

/** Keeps compilation errors separate from a Windows ZIP filesystem cleanup error. */
public final class CompileSources {
  public static void main(String[] args) {
    int separator=Arrays.asList(args).indexOf("--");
    if(separator<0)throw new IllegalArgumentException("Expected compiler options -- source files");
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    if(compiler==null)throw new IllegalStateException("A full JDK is required");
    DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
    StandardJavaFileManager files=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8);
    boolean success=false;
    try {
      Iterable<? extends JavaFileObject> sources=files.getJavaFileObjectsFromStrings(Arrays.asList(args).subList(separator+1,args.length));
      success=Boolean.TRUE.equals(compiler.getTask(null,files,diagnostics,Arrays.asList(args).subList(0,separator),null,sources).call());
    } catch(Throwable failure) {
      failure.printStackTrace();
    } finally {
      for(Diagnostic<?> diagnostic:diagnostics.getDiagnostics())System.err.println(diagnostic.getKind()+" "+diagnostic.getSource()+":"+diagnostic.getLineNumber()+" "+diagnostic.getMessage(Locale.ROOT));
      try { files.close(); }
      catch(java.io.IOException cleanup) { System.err.println("WARNING: Compiler file manager cleanup: "+cleanup); }
    }
    System.out.println("Compilation result: "+(success?"SUCCESS":"FAILED"));
    System.exit(success?0:1);
  }
}
