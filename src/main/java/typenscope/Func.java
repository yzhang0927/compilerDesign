package typenscope;

import parser.lingBorParser;
import parser.lingListener;
import parser.lingListener.*;
import java.util.ArrayList;
import java.util.List;

public class Func extends Symbol {

    private int line;
    private String name;
    private String returnType;
    private String inputType;
    private boolean isCall = false;
    private lingBorParser.DefContext ctx;

    public Func(String name, int line, lingBorParser.DefContext ctx){
        this.line = line;
        this.name = name;
        this.ctx = ctx;
    }

    public boolean checkInputValid(String ls){
        return this.inputType.equals(ls);
    }

    public String getInputType() {return this.inputType;}
    public String getReturnType() {
        return this.returnType;
    }
    public void setCalled(){
        this.isCall = true;
    }
    public boolean hasCalled(){
        return this.isCall;
    }
    public void setReturnType(String type) {
        this.returnType = type;
    }
    public lingBorParser.DefContext getCtx(){
        return this.ctx;
    }

    public void setInputType(String ls){
        this.inputType = ls;
        setCalled();
    }



}
