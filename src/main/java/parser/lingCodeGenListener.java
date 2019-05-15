package parser;

import typenscope.Func;
import typenscope.Symbol;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class lingCodeGenListener extends lingBorBaseListener {

    private String fileName = "src/main/java/codegen/output.ll";
    private int numPrintCall = 0;
    private int numCall = 0;
    private int numVar = 0;
    private int numOp = 0;
    private int numif = 0;

    private int stateForFun = 0;
    private BufferedWriter writer;

    HashMap<String, Symbol> symbolMap;
    HashMap<String, Symbol> funcVarMap;
    HashMap<String, Func> funcMap;
    HashMap<String, String> regSymbolMap = new HashMap<>();

    public void importMaps(HashMap<String, Symbol> symbolMap, HashMap<String, Func> funcMap){
        this.symbolMap = symbolMap;
        this.funcMap = funcMap;
    }

    public String getTypeByName(String idName){
        if(stateForFun==0){
            return symbolMap.get(idName).getType();
        } else {
            return funcVarMap.get(idName).getType();
        }
    }

    private void write(String str){
        try{
            writer.write(str);
        } catch (IOException e) {
            System.out.println("ERROR ! writing: "+ str + "error: " + e);
        }
    }

    private void dealWithDef(lingBorParser.InputContext ctx){
        for (lingBorParser.DefContext d:ctx.def()){

        }
    }

    @Override
    public void enterInput(lingBorParser.InputContext ctx) {
        try {
            writer = new BufferedWriter(new FileWriter(fileName));
        } catch (IOException e){
            System.out.println("ERROR ! Open target output file failed: " + e);
        }

        String header = "@.str = private unnamed_addr constant [4 x i8] c\"%d\\0A\\00\",align 1\n";
        write(header);

        dealWithDef(ctx);

        String mainHeader = "define i32 @main() #0 {\nentry:\n";
        write(mainHeader);

        // declare all global var in advance just as Clang does
        for (String name: symbolMap.keySet()){
            String varName = name;
            String varType = getTypeByName(name);
            if (varType=="INT_LIT"){
                write("  %"+varName+" = alloca i32, align 4\n");
            } else if(varType=="ARRAY"){
                    //ARRAY
            } else {
                    //TUPLE
                }
            }
    }

    @Override
    public void exitInput(lingBorParser.InputContext ctx) {
        try {
            write("  ret i32 0\n}\n");
            if (numPrintCall>0){
                write("declare i32 @printf(i8*, ...)\n");
            }
            writer.close();
        } catch (IOException e){
            System.out.println("ERROR ! codegen cannot close output file: " + e);
        }
    }


    private String performArithmaticOp(lingBorParser.ExprContext ctx,String l,String r){
        Integer left = Integer.parseInt(l);
        Integer right = Integer.parseInt(r);
        Integer ret = null;

        if(ctx.OP_DIV()!=null){
            ret =  left/right;
        } else if (ctx.OP_MULT()!=null){
            ret = left*right;
        } else if (ctx.OP_MINUS()!=null){
            ret =  left-right;
        } else if (ctx.OP_PLUS()!=null){
            ret =  left+right;
        }
        return String.valueOf(ret);
    }

    private String performRegisterOp(lingBorParser.ExprContext ctx,String l,String r){
        String opType = "";
        if(ctx.OP_DIV()!=null){
            opType = "sdiv";
        } else if (ctx.OP_MULT()!=null){
            opType = "mul";
        } else if (ctx.OP_MINUS()!=null){
            opType = "sub";
        } else if (ctx.OP_PLUS()!=null){
            opType = "add";
        }
        String ret = "%op"+numOp;
        write(String.format("  %s = %s nsw i32 %s, %s\n",ret,opType,l,r));
        numOp += 1;
        return ret;
    }

    private boolean isNumber(String str){
        if(str.contains("%")){return false;}
        else {return true;}
    }

    // if it's only arithmetic op between int_lit, get the answer, otherwise (id involved)
    // we may need to generate code on the run.
    private String evalExprRhs(lingBorParser.ExprContext ctx){
        if(ctx.int_lit() != null){
            return ctx.int_lit().INT_LIT().getSymbol().getText();
        } else if(ctx.id() != null){
            String idName = ctx.id().ID().getSymbol().getText();
            if(!regSymbolMap.containsKey(idName)){
                String regName = "%"+numVar;
                regSymbolMap.put(idName,regName);

                //String type = getTypeByName(idName);
                //I remember that no assign to array is permitted
                write(String.format("  %s = load i32, i32* %s, align 4\n",regName,"%"+idName));
                numVar+=1;
                return regName;
            } else {
                return regSymbolMap.get(idName);
            }

        } else if(ctx.OP_COMMA() != null){


        } else if(ctx.func_call() != null){
            //I am only considering integer, this need to be altered if you are using tuple as input parameter
            String inReg = evalExprRhs(ctx.func_call().expr());
            String funcName = ctx.func_call().id().ID().getSymbol().getText();
            write(String.format("  %s = call i32 @%s(i32 %s)\n","%call"+numCall,inReg,funcName));
            return "%call"+numCall;

        } else if(ctx.array_ele() != null){


        } else if(ctx.tuple_ele() != null){


        } else if(ctx.LPAR()!= null){
            // it means it is in the form of LPAR expr RPAR
            return evalExprRhs(ctx.expr(0));

        } else if(ctx.OP_PLUS() != null || ctx.OP_MINUS() != null
                || ctx.OP_MULT() != null ||ctx.OP_DIV() != null){
            //we can do this since the tree structure already put *,/ in the lower tree
            String left = evalExprRhs(ctx.expr(0));
            String right = evalExprRhs(ctx.expr(1));
            if (isNumber(left) && isNumber(right)){
                return performArithmaticOp(ctx,left,right);
            } else{
                return performRegisterOp(ctx,left,right);
            }
        }
        return null;
    }

    @Override public void enterDecl(lingBorParser.DeclContext ctx) {
        //KW_GLOBAL id (ASSIGN expr)? SEMI
        if(ctx.KW_GLOBAL() != null){
            if(ctx.ASSIGN() != null) {
                String outReg =  evalExprRhs(ctx.expr(0));
                regSymbolMap.clear();
                String targetVar = ctx.id(0).ID().getSymbol().getText();
                if(getTypeByName(targetVar)=="INT_LIT"){
                    write(String.format("  store i32 %s, i32* %s, align 4\n",outReg,"%"+targetVar));
                }
            }
        }
    }

    private String whichBoolOp(lingBorParser.Bool_exprContext ctx){
        String op = ctx.bool_op().getText();
        if(op=="OP_LESS"){
            return "slt";
        } else if(op=="OP_GREATER"){
            return "sgt";
        } else if(op=="OP_EQUAL") {
            return "eq";
        } else if(op=="OP_NOTEQUA"){
            return "ne";
        } else if(op=="OP_LESSEQUAL"){
            return "sle";
        } else if(op=="OP_GREATEREQUAL"){
            return "sge";
        } else {
            //not gonna happen
            return "";
        }
    }

    @Override public void enterStatement(lingBorParser.StatementContext ctx) {
        if(ctx.EXCHANGE()!=null) {

        } else if(ctx.ASSIGN()!=null){
            String outOp = evalExprRhs(ctx.expr());
            if(ctx.lhs(0).lhs_item().size()==1){
                String target = ctx.lhs(0).lhs_item(0).id().ID().getSymbol().getText();
                write(String.format("  store i32 %s, i32* %s, align 4",outOp,"%"+target));
                regSymbolMap.clear();
            } else {

            }
            //tbi for multiple lhs(multiple int, array not allowed)
            //maybe split into two sentences

        } else if(ctx.PRINT()!=null){
            String outFunc = "  %"+String.format("call%d = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([4 x i8], [4 x i8]* @.str, i64 0, i64 0), i32 ",numCall);
            String outOp = evalExprRhs(ctx.expr());
            regSymbolMap.clear();

            if (outOp == null){
            } else {
                outFunc = outFunc + outOp + ")\n";
            }
            numPrintCall += 1;
            numCall += 1;
            write(outFunc);

        } else if(ctx.bool_expr()!=null){
            for(int i=0; i<ctx.bool_expr().size(); i++) {
                if(i>0){
                    write(String.format("if.else%d:\n",numif));
                }
                String regLeft = evalExprRhs(ctx.bool_expr(0).expr(0));
                String regRight = evalExprRhs(ctx.bool_expr(0).expr(1));
                String op = whichBoolOp(ctx.bool_expr(0));
                write(String.format("  %s = icmp %s i32 %s, %s\n","%cmp"+i,op,regLeft,regRight));
                write(String.format("  br i1 %s, label %s, label %s\n","%cmp"+numif,"%if.then"+numif,"%if.else"+numif));
                write(String.format("if.then%d: ",i));
                numif += 1;
                enterStatement(ctx.statement(i));
                write("  br label %if.end\n");
            }

            if(ctx.KW_ELSE()!=null){
                write(String.format("if.else%d: \n",numif));
                enterStatement(ctx.statement(ctx.bool_expr().size()));
                write("  br label %if.end\n");
                write("if.end:\n");
                write(String.format("  %s = load i32, i32* %retval, align 4","%"+numVar));
                write(String.format("  ret i32 %s","%"+numVar));
                numVar += 1;
            }


        }
    }



}
