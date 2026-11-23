package hmgapi.hook;
public abstract class MethodHook { protected void beforeHookedMethod(Param p) throws Throwable {} protected void afterHookedMethod(Param p) throws Throwable {}
 public static final class Param { public final java.lang.reflect.Member method=null; public final Object thisObject=null; public final Object[] args=null;
  public Object getResult() { return null; } public void setResult(Object r) {} } }
