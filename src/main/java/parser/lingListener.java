package parser;

import com.sun.deploy.security.ValidationState;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import typenscope.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class lingListener extends lingBorBaseListener{

    private HashMap<String, Symbol> globalMap = new HashMap<>();
    private HashMap<String, Symbol> funcVarMap = new HashMap<>();
    private HashMap<String, Func> funcMap = new HashMap<>();

    private int statusForLoop = 0;
    private int statusFunc = 0;


    private boolean isDefinedInFuncMap(String funcName){
        return funcMap.containsKey(funcName);
    }

    private boolean isInSymbolMap(String idName) {
        if(statusForLoop!=0 && statusFunc==0){
            if (globalMap.containsKey(idName)){ return true; }
        } else if (statusForLoop ==0 && statusFunc==0){
            if (globalMap.containsKey(idName)) { return true; }
        } else if (statusFunc != 0 && statusForLoop ==0) {
            if (funcVarMap.containsKey(idName)) { return true; }
        } else {
            if (funcVarMap.containsKey(idName)) { return true; }
        }
        return false;
    }
    private boolean isDefinedInSymbolMap(String idName){
        if(statusForLoop!=0 && statusFunc==0){
            if (globalMap.get(idName).isDefined()){ return true; }
        } else if (statusForLoop ==0 && statusFunc==0){
            if (globalMap.get(idName).isDefined()) { return true; }
        } else if (statusFunc != 0 && statusForLoop ==0) {
            if (funcVarMap.get(idName).isDefined()) { return true; }
        } else {
            if (funcVarMap.get(idName).isDefined()) { return true; }
        }
        return false;
    }

    public String typeOfSymbol(Symbol s) {
        if (s.getClass() == Intlit.class){
            return "INT_LIT";
        } else if (s.getClass() == Arr.class) {
            return "ARRAY";
        } else if (s.getClass() == Tuple.class) {
            return "TUPLE,"+((Tuple)s).getLength();
        } else {
            return "UNDEFINED";
        }
    }

    private String getType(String idName, HashMap<String, Symbol> symbolMap) {
        try {
            Symbol s = symbolMap.get(idName);
            return typeOfSymbol(s);
        } catch (Exception en) {
            //System.out.println(String.format("ERROR! %s not declared in the symbol map", idName));
            return "UNDEFINED";
        }
    }

    private String getTypeByName(String idName) {
        if (statusForLoop == 0 && statusFunc == 0) {
            return getType(idName,globalMap);
        } else if (statusForLoop != 0 && statusFunc == 0 ) {
            return getType(idName,globalMap);
        } else if (statusForLoop == 0 && statusFunc!= 0 ) {
            return getType(idName, funcVarMap);
        } else{
            return getType(idName, funcVarMap);
        }
    }

    private void putSymbolByName(String idName,Symbol s){
        if (statusFunc == 0) {
            globalMap.put(idName,s);
        } else {
            funcVarMap.put(idName,s);
        }
    }

    private void popSymbolByName(String idName){
        if (statusFunc == 0) {
            globalMap.remove(idName);
        } else {
            funcVarMap.remove(idName);
        }
    }

    private Symbol getSymbolByName(String idName){
        if (statusForLoop == 0 && statusFunc == 0) {
            return globalMap.get(idName);
        } else if (statusForLoop != 0 && statusFunc == 0 ) {
            return globalMap.get(idName);
        } else if (statusForLoop == 0 && statusFunc!= 0 ) {
            return funcVarMap.get(idName);
        } else{
            return funcVarMap.get(idName);
        }
    }

    public Symbol createByContext(String name, int line, String type) {
        if(type=="INT_LIT"){
            return new Intlit(name,line);
        } else if (type=="ARRAY"){
            return new Arr(name,line);
        } else if (type.split(",", 2)[0].equals("TUPLE")){
            return new Tuple(name,line,Integer.parseInt(type.split(",",2)[1]));
        } else {
            return new Symbol(name,line);
        }
    }

    public Token getLineOfOp(lingBorParser.ExprContext ctx){
        if(ctx.OP_MINUS()!=null){return ctx.OP_MINUS().getSymbol();}
        else if (ctx.OP_PLUS()!=null){return ctx.OP_PLUS().getSymbol();}
        else if (ctx.OP_DIV()!=null){return ctx.OP_DIV().getSymbol();}
        else if (ctx.OP_MULT()!=null){return ctx.OP_MULT().getSymbol();}
        else {return null;} //I should not have done that.
    }


    public int checkByContextLhsItem(lingBorParser.Lhs_itemContext ctx){
        if(ctx.tuple_ele()!=null){
            checkByContextTupleEle(ctx.tuple_ele());
        } else if(ctx.array_ele()!=null) {
            checkByContextArrayEle(ctx.array_ele());
        } else if(ctx.id()!=null){
            String idName = ctx.id().ID().getSymbol().getText();
            int line = ctx.id().ID().getSymbol().getLine();
            String tmpType = getTypeByName(idName);
            if (tmpType=="UNDEFINED"){
                if(statusFunc==0) {
                    System.out.println(String.format("ERROR ! var:%s on line %d is not defined", idName, line));
                    return 1;
                } else {

                }
            } else if (Pattern.compile("TUPLE,[0-9]+").matcher(tmpType).matches()){
                return ((Tuple)getSymbolByName(idName)).getLength();
            }
        } else {
            System.out.println("wrong lhs expr");
        }
        return 1;
    }

    public int checkByContextLhsTuple(lingBorParser.LhsContext ctx) {
        if(ctx.lhs_item().size()==1){
            return checkByContextLhsItem(ctx.lhs_item(0));
        } else {
            int numOfElement=0;
            for(lingBorParser.Lhs_itemContext c:ctx.lhs_item()){
                numOfElement += checkByContextLhsItem(c);
            }
            return numOfElement;
        }
    }

    public String inferenceByContextLhs(lingBorParser.LhsContext ctx){
        if (ctx.lhs_item().size() > 1) {
            int numOfTuple = checkByContextLhsTuple(ctx);
            //System.out.println(ctx.lhs_item().size());
            return String.format("TUPLE,%d", numOfTuple);
        } else if (ctx.lhs_item(0) != null) {
            if (ctx.lhs_item(0).id() != null) {
                String idName = ctx.lhs_item(0).id().ID().getSymbol().getText();
                return getTypeByName(idName);
            } else if (ctx.lhs_item(0).array_ele() != null) {
                checkByContextArrayEle(ctx.lhs_item(0).array_ele());
                return "INT_LIT";
            } else if (ctx.lhs_item(0).tuple_ele() != null) {
                checkByContextTupleEle(ctx.lhs_item(0).tuple_ele());
                return "INT_LIT";
            }
        }
        return "UNDEFINED";
    }


    public String inferenceByContext(lingBorParser.ExprContext ctx) {
        if (ctx.OP_COMMA() != null) {

            int numOfTuple = checkByContextTuple(ctx);
            return String.format("TUPLE,%d", numOfTuple);
            //return "TUPLE";
        } else if (ctx.int_lit() != null) {
            return "INT_LIT";
        } else if (ctx.id() != null) {

            return getTypeByName(ctx.id().ID().getSymbol().getText());
        } else if (ctx.tuple_ele()!=null) {
            checkByContextTupleEle(ctx.tuple_ele());
            return "INT_LIT";
        } else if (ctx.array_ele()!=null) {
            checkByContextArrayEle(ctx.array_ele());
            return "INT_LIT";
        } else if (ctx.func_call()!=null){
            return checkByContextFunction(ctx.func_call());
        } else if (ctx.OP_DIV()!=null || ctx.OP_MULT()!=null || ctx.OP_PLUS()!=null || ctx.OP_MINUS()!=null) {
            Token info= this.getLineOfOp(ctx);
            if(inferenceByContext(ctx.expr(0))=="INT_LIT" && inferenceByContext(ctx.expr(1))=="INT_LIT") {
                return "INT_LIT";
            } else {
                if (statusFunc==0) {
                    System.out.println(String.format("ERROR ! The operation: %s on line %d cannot be performed between %s and %s",
                            info.getText(), info.getLine(), inferenceByContext(ctx.expr(0)), inferenceByContext(ctx.expr(1))));
                }
                return "INT_LIT";
            }
        } else if(ctx.LPAR()!=null){
            return inferenceByContext(ctx.expr(0));
        }
        return "UNDEFINED";
    }


    private String checkByContextFunction(lingBorParser.Func_callContext ctx) {
        if (ctx.id() != null) {
            String funcName = ctx.id().ID().getSymbol().getText();
            int line = ctx.id().ID().getSymbol().getLine();
            if (!isDefinedInFuncMap(funcName)) {
                System.out.println(String.format("ERROR, function %s on line %d is not defined", ctx.id().ID().getSymbol().getText(),line));
            } else {
                Func tmpFunc = funcMap.get(funcName);

                if (!tmpFunc.hasCalled()) {
                    tmpFunc.setInputType(inferenceByContext(ctx.expr()));
                    tmpFunc.setCalled();
                } else {
                    if(!tmpFunc.getInputType().equals(inferenceByContext(ctx.expr()))){
                        System.out.println(String.format("ERROR, function %s on line %d input parameter on should take %s instead of %s", funcName,line ,tmpFunc.getInputType(),inferenceByContext(ctx.expr())));
                    }
                }
            }
        }
        return "INT_LIT";
    }

    private int checkByContextTuple(lingBorParser.ExprContext ctx) {
            if (ctx.OP_COMMA() == null) {
                if (ctx.OP_PLUS()!=null||ctx.OP_MINUS()!=null||ctx.OP_MULT()!=null||ctx.OP_DIV()!=null){
                    inferenceByContext(ctx.expr(0));
                    inferenceByContext(ctx.expr(1));
                    return 1;
                } else if (ctx.id()!= null ) {
                    int line = ctx.id().ID().getSymbol().getLine();
                    if(!isDefinedInSymbolMap(ctx.id().ID().getSymbol().getText())){
                        System.out.println(String.format("Error ! var:%s in Tuple on line %d is not defined", ctx.id().ID().getSymbol().getText(),line));
                    } else if(getTypeByName(ctx.id().ID().getSymbol().getText())=="ARRAY"){
                        System.out.println(String.format("Error ! array:%s in a tuple on line %d is not allowed ", ctx.id().ID().getSymbol().getText(),line));
                    }
                } else if (ctx.array_ele() != null) {
                    int line = ctx.array_ele().id().ID().getSymbol().getLine();
                    if (!isDefinedInSymbolMap(ctx.array_ele().id().ID().getSymbol().getText())) {
                        System.out.println(String.format("Error ! array var:%s is not defined on line %d", ctx.array_ele().id().ID().getSymbol().getText(),line));
                    } else if (getTypeByName(ctx.array_ele().id().ID().getSymbol().getText()) != "ARRAY") {
                        System.out.println(String.format("Error ! array var:%s is not defined on line %d", ctx.array_ele().id().ID().getSymbol().getText()));
                    }
                } else if (ctx.func_call() != null) {
                    int line = ctx.func_call().id().ID().getSymbol().getLine();
                    if (!isDefinedInFuncMap(ctx.func_call().id().ID().getSymbol().getText())) {
                        System.out.println(String.format("Error ! func:%s is not defined on line %d", ctx.func_call().id().ID().getSymbol().getText()));
                    }
                } else if (ctx.tuple_ele() != null) {
                    int line = ctx.tuple_ele().id().ID().getSymbol().getLine();
                    if (!isDefinedInSymbolMap(ctx.tuple_ele().id().ID().getSymbol().getText())) {
                        System.out.println(String.format("Error ! tuple var:%s is not defined on line %d", ctx.tuple_ele().id().ID().getSymbol().getText()));
                    } else if (getTypeByName(ctx.tuple_ele().id().ID().getSymbol().getText()) != "TUPLE") {
                        System.out.println(String.format("Error ! tuple var:%s is not defined on line %d", ctx.tuple_ele().id().ID().getSymbol().getText()));
                    }
                }
                return 1;
            } else {
                return checkByContextTuple(ctx.expr(0)) + checkByContextTuple(ctx.expr(1));
            }
    }

    private void checkByContextTupleEle(lingBorParser.Tuple_eleContext ctx) {
        if(ctx.id()!=null) {
            int line = ctx.id().ID().getSymbol().getLine();
            String typeName = getTypeByName(ctx.id().ID().getSymbol().getText());
            if (statusFunc!=0 && typeName=="UNDEFINED"){
                return;
            }
            if (!Pattern.compile("TUPLE,[0-9]+").matcher(typeName).matches()) {
                System.out.println(String.format("ERROR! trying to access a non tuple var:%s type:%s on line %d", ctx.id().ID().getSymbol().getText(),typeName,line));
                return;

            }
            if (Integer.parseInt(ctx.int_lit().INT_LIT().getSymbol().getText()) >
                    ((Tuple) getSymbolByName(ctx.id().ID().getSymbol().getText())).getLength()) {
                System.out.println(String.format("ERROR! tuple index exceeds the length of tuple:%s on line %d", ctx.id().ID().getSymbol().getText(),ctx.id().ID().getSymbol().getLine()));
                return;
            }
        }
    }

    private void checkByContextArrayEle(lingBorParser.Array_eleContext ctx) {
        if(ctx.id()!=null) {
            if (getTypeByName(ctx.id().ID().getSymbol().getText()) != "ARRAY") {
                System.out.println(String.format("Error, array var%s is not defined", ctx.id().ID().getSymbol().getText()));
            }
        }
    }

    public int recursiveAddForDef(lingBorParser.ExprContext ctx) {
        if(ctx.OP_COMMA() == null){
            if (ctx.id()==null){
                System.out.println("ERROR! only id presentation allowed in function define");
                return 1;
            }

            String idName = ctx.id().ID().getSymbol().getText();
            int line = ctx.id().ID().getSymbol().getLine();
            System.out.println(String.format("SUCCESS! var:%s has been add as a local var in function on line %d",idName,line));
            putSymbolByName(idName,new Symbol(idName,line));
            return 1;

        }else {
            return recursiveAddForDef(ctx.expr(0)) + recursiveAddForDef(ctx.expr(1));
        }
    }

    @Override public void enterDef(lingBorParser.DefContext ctx) {
        System.out.println("");

        String funcName = ctx.id().ID().getSymbol().getText();
        int line = ctx.id().ID().getSymbol().getLine();

        if (!isDefinedInFuncMap(funcName)) {
            this.statusFunc += 1;

            Func tmpFunc = new Func(funcName,line,ctx);
            int i = recursiveAddForDef(ctx.expr());

            if(i>1){
                System.out.println(String.format("ERROR ! func:%s on line %d support only 1 formal param",funcName, line));
                return;
            }

            int flagReturnInvoked = 0;


            for(lingBorParser.StatementContext st:ctx.body().statement(0).statement()) {
                if(st.RETURN() != null) {

                    if (flagReturnInvoked == 0){
                        String typeName = inferenceByContext(st.expr());
                        if (typeName != "UNDEFINED") {
                            tmpFunc.setReturnType(typeName);
                            System.out.println(String.format("Type Infered! The return type is %s for func:%s on line %d",typeName,funcName,line));
                            flagReturnInvoked += 1;
                        }

                    } else if (flagReturnInvoked != 0) {
                        String firstReturnType = tmpFunc.getReturnType();
                        String secondReturnType = inferenceByContext(st.expr());
                        if(firstReturnType!=secondReturnType){
                            System.out.println(String.format("ERROR ! return type of func %s on line %d should be %s instead of %s",funcName,line,firstReturnType,secondReturnType));
                            return;
                        }
                    }

                }

            }
            funcMap.put(funcName,tmpFunc);
            System.out.println(String.format("SUCCESS ! Function: %s on line %d is created.",funcName,line));
        } else{
            System.out.println(String.format("ERROR ! Function: %s on line %d has been defined already.",funcName,line));
        }
    }

    @Override public void exitDef(lingBorParser.DefContext ctx) {
        funcVarMap.clear();
        this.statusFunc -= 1;
    }

    @Override public void enterDecl(lingBorParser.DeclContext ctx) {
        String idName = ctx.id(0).ID().getSymbol().getText();
        int line = ctx.id(0).ID().getSymbol().getLine();

        if (ctx.KW_ARRAY()!=null){
            if(isInSymbolMap(idName)&&!isDefinedInSymbolMap(idName)){
                putSymbolByName(idName,new Arr(idName,line));
                System.out.println(String.format("SUCCESS! The global array id:%s on line %d has been defined",idName,line));
            } else if(!isInSymbolMap(idName)){
                System.out.println(String.format("ERROR! The array:%s line %d has not been defined its scope",idName,line));

            } else if(isDefinedInFuncMap(idName)){
                String type = getTypeByName(idName);
                System.out.println(String.format("ERROR! The array:%s line %d has  been defined as a %s",idName,line,type));
            }
            return;
        }

        if (ctx.KW_LOCAL() != null) {
            if (statusFunc==0 && statusForLoop==0) {
                System.out.println(String.format("ERROR ! var:%s line %d, local cannot exists outside func", idName, line));
                return;
            } else if ( statusForLoop!=0){
                System.out.println(String.format("ERROR ! var:%s line %d, local cannot exists in loop", idName, line));
                return;
            } else {
                if(ctx.ASSIGN()!=null){ //ASSIGN exists
                    putSymbolByName(idName,createByContext(idName,line,inferenceByContext(ctx.expr(0))));
                    System.out.println(String.format("SUCCESS ! The local var:%s, type:%s on line %d has been declared", idName,inferenceByContext(ctx.expr(0)),line));
                } else { // no assign
                    putSymbolByName(idName, new Symbol(idName, line));
                }
            }

        } else if (ctx.KW_GLOBAL() != null) {
            //error
            if (statusForLoop != 0) {
                System.out.println(String.format("ERROR ! var:%s line %d, global cannot exists in loop", idName, line));
                return;
            } else if (statusFunc!= 0 ) {
                if(ctx.ASSIGN()!=null) {
                    System.out.println(String.format("ERROR ! A new global var:%s on line %d cannot be initialized in function",idName, line));
                } else {
                    if(globalMap.containsKey(idName) && globalMap.get(idName).isDefined()){
                        putSymbolByName(idName,globalMap.get(idName));
                        System.out.println(String.format("SUCCESS ! global var:%s on line %d is declared in function",idName, line));
                    } else {
                        System.out.println(String.format("ERROR ! global var:%s on line %d is not defined outside the function",idName, line));
                    }
                }
                return;
            }

            if (isInSymbolMap(idName)) {
                System.out.println(String.format("ERROR ! The global var:%s on line %d has been declared before", idName,line));
                return;
            }


            if(ctx.ASSIGN()!=null){ //ASSIGN exists
                putSymbolByName(idName,createByContext(idName,line,inferenceByContext(ctx.expr(0))));
                System.out.println(String.format("SUCCESS ! The global var:%s, type:%s on line %d has been declared", idName,inferenceByContext(ctx.expr(0)),line));
            } else { // no assign
                if(statusForLoop==0 && statusFunc==0) {
                    putSymbolByName(idName, new Symbol(idName, line));
                } else if(statusFunc!=0 && statusForLoop==0) {
                    if (globalMap.containsKey(idName)) {
                        putSymbolByName(idName, globalMap.get(idName));
                    }
                }
            }
        }

    }

    @Override public void enterFor_loop(lingBorParser.For_loopContext ctx) {

        if(ctx.array_id()!=null){
            String idName = ctx.array_id().id().ID().getSymbol().getText();
            int line = ctx.array_id().id().ID().getSymbol().getLine();

            if(getTypeByName(idName)!="ARRAY"){
                System.out.println(String.format("ERROR ! In foreach %s on line %d must be ARRAY instead of %s",idName,line,getTypeByName(idName)));
            }

        } else if (ctx.range()!=null){
            int line = ctx.range().OP_DOTDOT().getSymbol().getLine();
            if(inferenceByContext(ctx.range().expr(0))=="INT_LIT" && inferenceByContext(ctx.range().expr(1))=="INT_LIT"){
            }else{
                System.out.println(String.format("ERROR ! two expr in range on line %d must both be integers, now they are %s and %s",
                        line,inferenceByContext(ctx.range().expr(0)),inferenceByContext(ctx.range().expr(1))));
            }
        }

        if(ctx.expr().id()!= null) {
            String idName = ctx.expr().id().ID().getSymbol().getText();
            int line = ctx.expr().id().ID().getSymbol().getLine();
            Symbol idSymbol = new Intlit(idName,line);
            putSymbolByName(idName,idSymbol);
            this.statusForLoop += 1;
        } else {
            System.out.println("illegal expr found as foreach index var, id only!");
        }

    }

    @Override public void exitFor_loop(lingBorParser.For_loopContext ctx) {
        if(ctx.expr().id()!= null) {
            String idName = ctx.expr().id().ID().getSymbol().getText();
            int line = ctx.expr().id().ID().getSymbol().getLine();
            this.statusForLoop -= 1;
            popSymbolByName(idName);
        }
    }

    @Override public void enterWhile_loop(lingBorParser.While_loopContext ctx) {
        this.statusForLoop += 1;
    }

    @Override public void exitWhile_loop(lingBorParser.While_loopContext ctx) {
        this.statusForLoop -= 1;
    }


    @Override public void enterStatement(lingBorParser.StatementContext ctx) {

        if (ctx.PRINT() != null) {
            if (ctx.expr() != null) {
                String tmpType = inferenceByContext(ctx.expr());
            }

        } else if (ctx.RETURN() != null) {
            if (ctx.expr() != null) {
                String tmpType = inferenceByContext(ctx.expr());
                if (tmpType == "ARRAY"){
                    System.out.println(String.format("ERROR ! We cannot return array type on line in a function %d",ctx.RETURN().getSymbol().getLine()));
                }

            }

        } else if (ctx.ASSIGN() != null) {
            int line = ctx.ASSIGN().getSymbol().getLine();

            String rightType = inferenceByContext(ctx.expr());
            String leftType = inferenceByContextLhs(ctx.lhs(0));

            if(statusFunc!=0 && leftType == "UNDEFINED" && rightType != "UNDEFINED" && ctx.lhs(0).lhs_item(0).id()!=null && ctx.expr()!=null){
                leftType = rightType;
                Symbol tmp = getSymbolByName(ctx.lhs(0).lhs_item(0).id().ID().getSymbol().getText());
                System.out.println(String.format("Attempt to perform left type inferencing, now var:%s is a %s",tmp.getName(),leftType));
                putSymbolByName(tmp.getName(),createByContext(tmp.getName(),tmp.getLine(),leftType));
                return;
            }
            if(statusFunc!=0 && rightType == "UNDEFINED" && leftType != "UNDEFINED" && ctx.lhs(0).lhs_item(0).id()!=null && ctx.expr().id()!=null){
                rightType = leftType;
                Symbol tmp = getSymbolByName(ctx.expr().id().ID().getSymbol().getText());
                System.out.println(String.format("Attempt to perform right type inferencing, now var:%s is a %s",tmp.getName(),rightType));
                putSymbolByName(tmp.getName(),createByContext(tmp.getName(),tmp.getLine(),rightType));
                return;
            }

            if (leftType != rightType) {
                System.out.println(String.format("ERROR ! ASSIGN statement on line %d invalid! %s cannot be assigned with a %s", line,leftType, rightType));
            } else if (leftType == "ARRAY"){
                System.out.println(String.format("ERROR ! ASSIGN statement on line %d invalid! an entire array cannot be assigned",line));
            }


        } else if (ctx.EXCHANGE() != null) {

            String rightType = inferenceByContext(ctx.expr());
            String leftType = inferenceByContextLhs(ctx.lhs(0));

            if (leftType != rightType) {
                System.out.println(String.format("ERROR ! var:%s on line %d cannot be assigned with %s", leftType, rightType));
            }

        }
    }

    @Override public void exitStatement(lingBorParser.StatementContext ctx) { }

    @Override public void exitBool_expr(lingBorParser.Bool_exprContext ctx) {
        if (ctx.expr()!=null){
            if (inferenceByContext(ctx.expr(0))=="INT_LIT" && inferenceByContext(ctx.expr(1))=="INT_LIT") {
            } else {
                System.out.println("ERROR ! bool op only support INT_LIT instead of "+
                        inferenceByContext(ctx.expr(0))+" and "+ inferenceByContext(ctx.expr(1)));
            }
        }
    }

    @Override public void enterExpr(lingBorParser.ExprContext ctx) {

    }

    @Override public void exitExpr(lingBorParser.ExprContext ctx) {

    }

}
