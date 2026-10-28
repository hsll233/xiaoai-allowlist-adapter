package local.codex.xiaoaiallowlist;
import java.lang.reflect.*;
import java.util.*;

/** Metadata only: never reads a singleton or initializes a host class. */
public final class SignatureProbe {
  public static List<String> issues(ClassLoader loader,String[][] bindings){
    List<String> missing=new ArrayList<String>();
    for(String[] b:bindings)try{
      Class<?> owner=Class.forName(b[1],false,loader);
      if("C".equals(b[0]))continue;
      if("K".equals(b[0])){owner.getDeclaredConstructor();continue;}
      boolean found=false;
      for(Class<?> c=owner;c!=null&&!found;c=c.getSuperclass()){
        if("F".equals(b[0])){
          try{Field f=c.getDeclaredField(b[2]);found=Modifier.isStatic(f.getModifiers());}catch(NoSuchFieldException ignored){}
        }else for(Method m:c.getDeclaredMethods()){
          if(!m.getName().equals(b[2]))continue;
          if("N".equals(b[0])){found=m.getParameterTypes().length==Integer.parseInt(b[3]);}
          else{
            Class<?>[] p=m.getParameterTypes();found=p.length==b.length-3;
            for(int i=0;found&&i<p.length;i++)found=p[i].getName().equals(b[i+3]);
          }
          if(found)break;
        }
      }
      if(!found)missing.add(b[1]+"."+b[2]);
    }catch(Throwable e){missing.add(b[1]+(b.length>2?"."+b[2]:""));}
    return missing;
  }
}
