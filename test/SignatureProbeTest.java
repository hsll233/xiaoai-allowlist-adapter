package local.codex.xiaoaiallowlist;
import java.util.*;
public final class SignatureProbeTest {
  public static final class Marker{static boolean initialized;}
  public static class Parent{private void inherited(String x){}}
  public static final class Host extends Parent{
    static{Marker.initialized=true;}
    public static Host a; public int instance;
    public Host(){} public void postEvent(String x){} public void postEvent(int x){}
    public void withTwo(String a,int b){}
  }
  public static void main(String[] args){
    String name=SignatureProbeTest.class.getName()+"$Host";ClassLoader loader=SignatureProbeTest.class.getClassLoader();int checks=0;
    String[][] valid={{"C",name},{"K",name},{"F",name,"a"},{"M",name,"postEvent","java.lang.String"},{"M",name,"postEvent","int"},{"M",name,"inherited","java.lang.String"},{"N",name,"withTwo","2"}};
    if(!SignatureProbe.issues(loader,valid).isEmpty())throw new AssertionError("Valid bindings");checks++;
    if(Marker.initialized)throw new AssertionError("Host initialized during metadata probe");checks++;
    for(String[] bad:new String[][]{{"C","missing.Host"},{"F",name,"instance"},{"F",name,"missing"},{"M",name,"postEvent","boolean"},{"N",name,"withTwo","1"}}){
      if(SignatureProbe.issues(loader,new String[][]{bad}).size()!=1)throw new AssertionError(Arrays.toString(bad));checks++;
    }
    if(!SignatureProbe.issues(loader,new String[0][]).isEmpty())throw new AssertionError("Empty bindings");checks++;
    System.out.println(checks+" compatibility checks passed");
  }
}
