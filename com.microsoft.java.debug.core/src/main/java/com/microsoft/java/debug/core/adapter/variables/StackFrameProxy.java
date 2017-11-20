package com.microsoft.java.debug.core.adapter.variables;

import java.util.List;
import java.util.Map;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

public class StackFrameProxy implements StackFrame {
    private final int depth;
    private final int hash;
    private StackFrame proxy;
    private final StoppedState stopState;
    public StackFrameProxy(StoppedState state, StackFrame stackFrame, int depth) {
        stopState = state;
        proxy = stackFrame;
        this.depth = depth;
        hash = Long.hashCode(state.getVersion()) + stackFrame.thread().hashCode() + depth;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj.getClass() != this.getClass()){
            return false;
        }
        if(this == obj){
            return true;
        }
        StackFrameProxy sf = (StackFrameProxy) obj;
        return stopState == sf.stopState && depth == sf.depth;

    }

    public StoppedState getStoppedState() {
        return stopState;
    }
    public int getDepth() {
        return depth;
    }

  @Override
  public VirtualMachine virtualMachine() {
      return proxy.virtualMachine();
  }


  @Override
  public Location location() {
      return proxy.location();
  }

  @Override
  public ThreadReference thread() {
      return proxy.thread();
  }


  @Override
  public List<Value> getArgumentValues() {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          return proxy.getArgumentValues();
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              return proxy.getArgumentValues();
          }
          throw ex;
      }
  }

  @Override
  public Value getValue(LocalVariable arg0) {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          return proxy.getValue(arg0);
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              return proxy.getValue(arg0);
          }
          throw ex;
      }
  }

  @Override
  public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> arg0) {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          return proxy.getValues(arg0);
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              return proxy.getValues(arg0);
          }
          throw ex;
      }
  }

  @Override
  public void setValue(LocalVariable arg0, Value arg1) throws InvalidTypeException, ClassNotLoadedException {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          proxy.setValue(arg0, arg1);
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              proxy.setValue(arg0, arg1);
              return;
          }
          throw ex;
      }
  }

  @Override
  public ObjectReference thisObject() {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          return proxy.thisObject();
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              return proxy.thisObject();
          }
          throw ex;
      }
  }


  @Override
  public LocalVariable visibleVariableByName(String arg0) throws AbsentInformationException {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          return proxy.visibleVariableByName(arg0);
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              return proxy.visibleVariableByName(arg0);
          }
          throw ex;
      }
  }

  @Override
  public List<LocalVariable> visibleVariables() throws AbsentInformationException {
      if (proxy == null) {
          throw new InvalidStackFrameException();
      }
      try {
          return proxy.visibleVariables();
      } catch (InvalidStackFrameException ex) {
          if (stopState != null) {
              proxy = stopState.refreshStackFrames(depth);
              if (proxy == null) {
                  throw ex;
              }
              return proxy.visibleVariables();
          }
          throw ex;
      }
  }


}
