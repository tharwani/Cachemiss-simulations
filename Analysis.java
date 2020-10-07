import java.util.*;
import static java.util.stream.Collectors.*;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

// class IntVariable{
//     String name;
//     int val;
//     IntVariable(String nam,int vl){
//         val=vl;
//         name=nam;
//     }
// }

// class StrVariable{
//     String name;
//     String val;
//     StrVariable(String nam,String vl){
//         val=vl;
//         name=nam;
//     }
// }

// class ArrVariable{
//     String name;
//     int[] dim;
//     int type; // see mapping in the typeToSize
//     long cachemisses; // storing the answer
//     ArrVariable(String nam,int[] vl,int t){
//         dim = vl.clone();
//         name=nam;
//         type=t;
//     }
// }
// // wrapper class for hashmap
// class Variable{
//     int type; //0:Int 1:str 2:arr
//     IntVariable intv;
//     StrVariable strv;
//     ArrVariable arrv;
//     Variable(int t){
//         type=t;
//     }
//     void insert_intv(IntVariable v){
//         intv = v;
//     }
//     void insert_strv(StrVariable v){
//         strv = v;
//     }
//     void insert_arrv(ArrVariable v){
//         arrv = v;
//     }
// }

// class SymbolTable {
//     // declaration, add elements method and constructor
//     HashMap <String,Variable> table;
//     SymbolTable(){
//         table = new HashMap<String,Variable>();
//     }
//     void insert(String a, Variable v){
//         table.put(a,v);
//     } 
// }

// FIXME: You should limit your implementation to this class. You are free to add new auxilliary classes. You do not need to touch the LoopNext.g4 file.
class Analysis extends LoopNestBaseListener {

    // Possible types
    enum Types {
        Byte, Short, Int, Long, Char, Float, Double, Boolean, String
    }

    // Type of variable declaration
    enum VariableType {
        Primitive, Array, Literal
    }

    // Types of caches supported
    enum CacheTypes {
        DirectMapped, SetAssociative, FullyAssociative,
    }

    // auxilliary data-structure for converting strings
    // to types, ignoring strings because string is not a
    // valid type for loop bounds
    final Map<String, Types> stringToType = Collections.unmodifiableMap(new HashMap<String, Types>() {
        private static final long serialVersionUID = 1L;

        {
            put("byte", Types.Byte);
            put("short", Types.Short);
            put("int", Types.Int);
            put("long", Types.Long);
            put("char", Types.Char);
            put("float", Types.Float);
            put("double", Types.Double);
            put("boolean", Types.Boolean);
        }
    });

    // auxilliary data-structure for mapping types to their byte-size
    // size x means the actual size is 2^x bytes, again ignoring strings
    final Map<Types, Integer> typeToSize = Collections.unmodifiableMap(new HashMap<Types, Integer>() {
        private static final long serialVersionUID = 1L;

        {
            put(Types.Byte, 0);
            put(Types.Short, 1);
            put(Types.Int, 2);
            put(Types.Long, 3);
            put(Types.Char, 1);
            put(Types.Float, 2);
            put(Types.Double, 3);
            put(Types.Boolean, 0);
        }
    });

    // Map of cache type string to value of CacheTypes
    final Map<String, CacheTypes> stringToCacheType = Collections.unmodifiableMap(new HashMap<String, CacheTypes>() {
        private static final long serialVersionUID = 1L;

        {
            put("FullyAssociative", CacheTypes.FullyAssociative);
            put("SetAssociative", CacheTypes.SetAssociative);
            put("DirectMapped", CacheTypes.DirectMapped);
        }
    });

    ///
    class IntVariable {
        String name;
        int val;
        boolean itr=false;
        int stride=0;
        int intl,fnl; // initial and final values of iterator
        int nest_lvl; // 0 for outer most level
        int steps; //
        IntVariable(String nam,int vl){
            val=vl;
            name=nam;
        }
        void updateStride(int l){
            stride=l;
        }
        void updateFnl(int l){
            fnl=l;
        }
        void updateIntl(){
            intl=val;
            itr=true;
        }
        void updateNestLvl(int l){
            nest_lvl=l;
        }
        void updateSteps(){
            steps = (fnl-intl)/stride;
        }
        // Object clone() throws 
        // CloneNotSupportedException
        // { 
        //     return super.clone(); 
        // }        
    }
    
    class StrVariable{
        String name;
        String val;
        StrVariable(String nam,String vl){
            val=vl;
            name=nam;
        }
    }
    
    class ArrVariable{
        String name;
        int[] dim;
        int elem_size; // in B
        long cachemisses = 1; // storing the answer
        // boolean isCaculated = false;
        HashMap <Integer,ArrayList<HashMap<String,Integer>>> nest_order; // outer map consists of occurences of array according to nesting levels
        // the inner map contains the order of iterators for array dimension A[i][j][k] => {i:0,j:1,k:2}
        ArrVariable(String nam,int[] vl,int t){
            dim = vl.clone();
            name=nam;
            elem_size = (int) Math.pow(2,t);
            nest_order = new HashMap<Integer,ArrayList<HashMap<String,Integer>>>();
        }
        //
        void update_map(int nst_order, HashMap<String,Integer> var_order){
            if(Objects.nonNull(nest_order.get(nst_order))){
                nest_order.get(nst_order).add(var_order);
            }
            else{
                ArrayList<HashMap<String,Integer>> a = new ArrayList<HashMap<String,Integer>>();
                a.add(var_order);
                nest_order.put(nst_order,a);
            }
            // int a = table.table.get("cacheType").intv.val;
        }
    }
    // wrapper class for hashmap
    class Variable{
        int type; //0:Int 1:str 2:arr
        IntVariable intv;
        StrVariable strv;
        ArrVariable arrv;
        Variable(int t){
            type=t;
        }
        void insert_intv(IntVariable v){
            intv = v;
        }
        void insert_strv(StrVariable v){
            strv = v;
        }
        void insert_arrv(ArrVariable v){
            arrv = v;
        }
    }
    class Cache {
        long cachePower;
        long blockPower;
        CacheTypes cacheType;
        long setSize;
        long setLines;
        Cache(int a, int b, String c, long d){
            cachePower = (long) Math.pow(2, a);
            blockPower = (long) Math.pow(2, b);
            cacheType = stringToCacheType.get(c);
            setSize = d;
            // System.out.println(setSize);
            setLines = cachePower/(blockPower*setSize);
        }
    }

    class SymbolTable {
        // declaration, add elements method and constructor
        HashMap <String,Variable> table;
        HashMap <Integer,String> depth_var_map;
        ArrayList<String> arr_names;
        int total_iterators = 0; 
        int nest_lvl = -1; //0 means outer most level
        SymbolTable(){
            table = new HashMap<String,Variable>();
            depth_var_map = new HashMap<Integer,String>();
            arr_names= new ArrayList<String>();
        }
        void insert(String a, Variable v){
            table.put(a,v);
        }
        void incLvl(){
            nest_lvl+=1;
        }
        void decLvl(){
            nest_lvl-=1;
        }
        void insert2(int i, String var){
            depth_var_map.put(i,var);
        }
    }
    
    ///////

    SymbolTable table;
    Cache cache;
    HashMap<String,Long> ans;
    List<HashMap<String,Long>> ans_list = new ArrayList<HashMap<String,Long>>();

    public Analysis() {
        // table = new SymbolTable();
        // System.out.println(table.table);
    }

    // FIXME: Feel free to override additional methods from
    // LoopNestBaseListener.java based on your needs.
    // Method entry callback
    @Override
    public void enterMethodDeclaration(LoopNestParser.MethodDeclarationContext ctx) {
        // System.out.println("enterMethodDeclaration");
        table = new SymbolTable();
        ans = new HashMap<String,Long>();
    }

    // End of testcase
    @Override
    public void exitMethodDeclaration(LoopNestParser.MethodDeclarationContext ctx) {
        // System.out.println("exitMethodDeclaration");
        // System.out.println(table.table);
        int a = table.table.get("cachePower").intv.val;
        int b = table.table.get("blockPower").intv.val;
        long s=1;
        String v = table.table.get("cacheType").strv.val;
        // System.out.println(v);
        if(v.equals("\"SetAssociative\"")){
            s=table.table.get("setSize").intv.val;
            // System.out.println(s);
        }
        else if(v.equals("\"FullyAssociative\"")){
            s=(long) (Math.pow(2,a-b));
        }
        cache = new Cache(a,b,v,s);
        // System.out.println(cache.blockPower + " " + cache.cachePower + " " + cache.setLines + " "+cache.setSize);
        // Case 1 : DMC
        for(String arr : table.arr_names){
            ArrVariable ar = table.table.get(arr).arrv;
            for(int l=table.total_iterators-1;l>=0;l--){
                HashMap<String,Integer> h;
                if(Objects.nonNull(ar.nest_order.get(l))){
                    h = ar.nest_order.get(l).get(ar.nest_order.get(l).size()-1);
                    // for(String l1 : h.keySet()) System.out.println(l1+" : "+h.get(l1));
                }
                else continue; 
                int m=table.total_iterators-1;
                long sim_setlines,sim_setSize,effective_cachePower; // in terms of blocks, effective cachePower
                effective_cachePower = cache.cachePower/cache.blockPower;
                long cachemisses = 1;
                int lowest_access_iterator = 0;
                int mode = 0; // effective_cachePower v/s loop_accesses 0:>,1:=,2:<
                long dim0 = 1;
                long dim1 = 1;
                long dim2 = 1;
                double offset0,offset1,offset2;
                sim_setlines = 1;
                sim_setSize = 1;
                offset0 = 1;                
                offset1 = 1;                
                offset2 = 1;                
                // IntVariable itrv0,itrv1,itrv2;
                while(m>=0){
                    IntVariable itrv = table.table.get(table.depth_var_map.get(m)).intv;
                    // System.out.println(" h.get(itrv.name) : "+h.get(itrv.name)+ " itrv.name : "+itrv.name);
                    if(mode==0){
                        if(h.containsKey(itrv.name)){
                            // System.out.println("hi");
                            if(lowest_access_iterator==0){ // lowest iteration
                                // itrv = (IntVariable) itrv.clone();
                                // System.out.println("hi");
                                long loop_access_lines,setlines_access;
                                int access_dim = ar.dim.length - h.get(itrv.name)-1;
                                for(int k=0;k<access_dim;k++){
                                    dim0*=ar.dim[ar.dim.length-k-1];
                                }
                                offset0 = 1.0*dim0*itrv.stride*ar.elem_size/cache.blockPower; // offset in terms of blocks to be skipped in this iteration 
                                // System.out.println("offset0 : "+offset0);
                                setlines_access = Math.min((long) (cache.setLines/offset0),cache.setLines);
                                // System.out.println("cache.setLines : "+cache.setLines);
                                if(2*offset0 <= 1){
                                    if(1 >= itrv.steps*offset0){ // no cache misses
                                        // handle later
                                    }
                                    else{
                                        loop_access_lines = (long) (itrv.steps*offset0);
                                        cachemisses*=loop_access_lines;
                                        if(loop_access_lines>setlines_access){
                                            sim_setlines = Math.max(setlines_access,1);
                                            sim_setSize *= loop_access_lines/sim_setlines;
                                        }
                                        else sim_setlines = loop_access_lines;
                                        if(sim_setSize>cache.setSize) mode=1;
                                        // System.out.println(loop_access_lines + " " + setlines_access +" " + sim_setlines + " " +sim_setSize) ;
                                    }
                                }
                                else{
                                    loop_access_lines = itrv.steps;
                                    cachemisses*=loop_access_lines;
                                    if(loop_access_lines>setlines_access){
                                        sim_setlines = Math.max(setlines_access,1);
                                        sim_setSize *= loop_access_lines/sim_setlines;
                                    }
                                    else sim_setlines = loop_access_lines;
                                    if(sim_setSize>cache.setSize) mode=1;
                                    // System.out.println(loop_access_lines + " " + setlines_access +" " + sim_setlines + " " +sim_setSize) ;
                                }
                                lowest_access_iterator+=1;
                            }
                            else if(lowest_access_iterator==1){ // second iterator
                                // itrv1 = (IntVariable) itrv.clone();
                                int access_dim = ar.dim.length - h.get(itrv.name)-1;
                                for(int k=0;k<access_dim;k++) dim1*=ar.dim[ar.dim.length-k-1];
                                long loop_access_lines,setlines_access;
                                offset1 = 1.0*dim1*itrv.stride*ar.elem_size/cache.blockPower; // offset in terms of blocks to be skipped for  this iteration 
                                setlines_access = Math.min((long) (cache.setLines/offset1),cache.setLines);;
                                if(2*offset1 <= 1){
                                    if(1 >= itrv.steps*offset1){ // no cache misses
                                        // handle later
                                    }
                                    else{
                                        loop_access_lines = (long) (itrv.steps*offset1);
                                        cachemisses*=loop_access_lines;
                                        if(loop_access_lines>setlines_access){
                                            sim_setlines = Math.max(setlines_access,1);
                                            sim_setSize *= loop_access_lines/sim_setlines;
                                        }
                                        else sim_setlines = loop_access_lines;
                                        if(sim_setSize>cache.setSize) mode=1;
                                    }
                                }
                                else{
                                    loop_access_lines = itrv.steps;
                                    cachemisses*=loop_access_lines;
                                    if(loop_access_lines>setlines_access){
                                        sim_setlines = Math.max(setlines_access,1);
                                        sim_setSize *= loop_access_lines/sim_setlines;
                                    }
                                    else sim_setlines = loop_access_lines;
                                    if(sim_setSize>cache.setSize) mode=1;
                                }
                                lowest_access_iterator+=1;
                            }
                            else{ // third iterator
                                // itrv2 = (IntVariable) itrv.clone();
                                // System.out.println("hi");
                                int access_dim = ar.dim.length - h.get(itrv.name)-1;
                                // System.out.println(itrv.name);
                                for(int k=0;k<access_dim;k++) dim2*=ar.dim[ar.dim.length-k-1];
                                long loop_access_lines,setlines_access;
                                // System.out.println("dim2 : " + dim2);
                                offset2 = 1.0*dim2*itrv.stride*ar.elem_size/cache.blockPower; // offset in terms of blocks to be skipped for  this iteration 
                                // System.out.println("offset2 : " + offset2);
                                setlines_access = Math.min((long) (cache.setLines/offset2),cache.setLines);;
                                if(2*offset2 <= 1){
                                    // System.out.println("hi");
                                    if(1 >= itrv.steps*offset2){ // no cache misses

                                        // handle later
                                    }
                                    else{
                                        loop_access_lines = (long) (itrv.steps*offset2);
                                        cachemisses*=loop_access_lines;
                                        // System.out.println("hi");
                                        if(loop_access_lines>setlines_access){
                                            // System.out.println(sim_setlines+" "+setlines_access+" "+sim_setSize);
                                            sim_setlines = Math.max(setlines_access,1);
                                            sim_setSize *= loop_access_lines/sim_setlines;
                                        }
                                        else sim_setlines = loop_access_lines;
                                        if(sim_setSize>cache.setSize) mode=1;
                                    }
                                }
                                else{
                                    loop_access_lines = itrv.steps;
                                    cachemisses*=loop_access_lines;
                                    if(loop_access_lines>setlines_access){
                                        sim_setlines = Math.max(setlines_access,1);
                                        sim_setSize *= loop_access_lines/sim_setlines;
                                    }
                                    else sim_setlines = loop_access_lines;
                                    if(sim_setSize>cache.setSize) mode=1;
                                }
                                lowest_access_iterator+=1;
                            }
                        }
                    }
                    else{
                        cachemisses*=itrv.steps; 
                    }
                    // System.out.println("mode ; "+mode);
                    m--;                        //
                }
                table.table.get(arr).arrv.cachemisses+=cachemisses;
                // table.table.get(arr).arrv.isCaculated = true;
                ans.put(arr,cachemisses);
                // System.out.println(arr+" : "+cachemisses);
            }
        }
        // Case 2 : set-associative

        // Case 3 : fully associative

        // final logic here
        ans_list.add(ans);
    }

    @Override
    public void exitTests(LoopNestParser.TestsContext ctx) {
        try {
            FileOutputStream fos = new FileOutputStream("Results.obj");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            // FIXME: Serialize your data to a file
            oos.writeObject(ans_list);
            oos.close();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void exitLocalVariableDeclaration(LoopNestParser.LocalVariableDeclarationContext ctx) {

    }

    @Override
    public void exitVariableDeclarator(LoopNestParser.VariableDeclaratorContext ctx) {
        // System.out.println("literal "+Objects.isNull(ctx.literal()));
        // System.out.println("literal "+Objects.isNull(ctx.arrayCreationExpression()));
        if(Objects.nonNull(ctx.literal())){
            //integer arguments method, assuming only int data type
            if(Objects.nonNull(ctx.literal().IntegerLiteral())){
                String var = ctx.variableDeclaratorId().Identifier().getText();
                int val = Integer.parseInt(ctx.literal().IntegerLiteral().getText());
                IntVariable v = new IntVariable(var,val);
                Variable v1 = new Variable(0);
                v1.insert_intv(v);
                table.insert(var, v1);
                // System.out.println("val : "+table.table.get(var).intv.val);
            }
            else if(Objects.nonNull(ctx.literal().StringLiteral())){ // string arguments method
                String var = ctx.variableDeclaratorId().Identifier().getText();
                String val = ctx.literal().StringLiteral().getText();
                StrVariable v = new StrVariable(var,val);
                Variable v1 = new Variable(1);
                v1.insert_strv(v);
                table.insert(var, v1);
            }
            // System.out.println("val : "+ctx.literal().IntegerLiteral().getText() + "rule :"+ctx.literal().getRuleIndex());
        }
        else if(Objects.nonNull(ctx.arrayCreationExpression())){
            // complete implementation here, for dimensions, checkt the length of list
            String var = ctx.variableDeclaratorId().Identifier().getText();
            int type,l;
            type = 2; //default to Int
            if(Objects.nonNull(ctx.arrayCreationExpression().unannPrimitiveType().numericType().integralType())){
                type = typeToSize.get(stringToType.get(ctx.arrayCreationExpression().unannPrimitiveType().numericType().integralType().getText()));
            }
            else if(Objects.nonNull(ctx.arrayCreationExpression().unannPrimitiveType().numericType().floatingPointType())){
                type = typeToSize.get(stringToType.get(ctx.arrayCreationExpression().unannPrimitiveType().numericType().floatingPointType().getText()));
            }
            l=ctx.arrayCreationExpression().dimExprs().dimExpr().size();
            int[] dim = new int[l];
            for(int i=0;i<l;i++){
                if(Objects.nonNull(ctx.arrayCreationExpression().dimExprs().dimExpr(i).expressionName())){
                    dim[i] = table.table.get(ctx.arrayCreationExpression().dimExprs().dimExpr(i).expressionName().Identifier().getText()).intv.val;
                }
                else{
                    dim[i] = Integer.parseInt(ctx.arrayCreationExpression().dimExprs().dimExpr(i).IntegerLiteral().getText());
                }
            }
            ArrVariable a = new ArrVariable(var, dim, type);
            Variable v1 = new Variable(2);
            v1.insert_arrv(a);
            table.insert(var, v1);
            table.arr_names.add(var);
            // System.out.println("val : "+table.table.get(var).arrv.dim[0]);
        }
    }

    @Override
    public void exitArrayCreationExpression(LoopNestParser.ArrayCreationExpressionContext ctx) {
    }

    @Override
    public void exitDimExprs(LoopNestParser.DimExprsContext ctx) {
    }

    @Override
    public void exitDimExpr(LoopNestParser.DimExprContext ctx) {
    }

    @Override
    public void exitLiteral(LoopNestParser.LiteralContext ctx) {
    }

    @Override
    public void exitVariableDeclaratorId(LoopNestParser.VariableDeclaratorIdContext ctx) {
    }

    @Override
    public void exitUnannArrayType(LoopNestParser.UnannArrayTypeContext ctx) {
    }

    @Override
    public void enterDims(LoopNestParser.DimsContext ctx) {
    }

    @Override
    public void exitUnannPrimitiveType(LoopNestParser.UnannPrimitiveTypeContext ctx) {
    }

    @Override
    public void exitNumericType(LoopNestParser.NumericTypeContext ctx) {
    }

    @Override
    public void exitIntegralType(LoopNestParser.IntegralTypeContext ctx) {
    }

    @Override
    public void enterForStatement(LoopNestParser.ForStatementContext ctx) {
        table.incLvl();
    }

    @Override
    public void exitForStatement(LoopNestParser.ForStatementContext ctx) {
        table.decLvl();
    }


    @Override
    public void exitFloatingPointType(LoopNestParser.FloatingPointTypeContext ctx) {
    }

    @Override
    public void exitForInit(LoopNestParser.ForInitContext ctx) {
        // do decide level of iterators
        // table.updateLvl();
        String var = ctx.localVariableDeclaration().variableDeclarator().variableDeclaratorId().Identifier().getText();
        table.table.get(var).intv.updateIntl();
        table.table.get(var).intv.updateNestLvl(table.nest_lvl);
        table.insert2(table.nest_lvl, var);
        table.total_iterators+=1;
    }

    @Override
    public void exitForCondition(LoopNestParser.ForConditionContext ctx) {
        if(Objects.isNull(ctx.relationalExpression().shiftExpression())){
            int f;
            if(ctx.relationalExpression().expressionName().size()>1){
                f=table.table.get(ctx.relationalExpression().expressionName(1).Identifier().getText()).intv.val;
            }
            else{
                f=Integer.parseInt(ctx.relationalExpression().IntegerLiteral().getText());
            }
            table.table.get(ctx.relationalExpression().expressionName(0).Identifier().getText()).intv.updateFnl(f);
        }
    }

    @Override
    public void exitRelationalExpression(LoopNestParser.RelationalExpressionContext ctx) {
    }

    @Override
    public void exitForUpdate(LoopNestParser.ForUpdateContext ctx) {
        // write the method for deciding a variable is iterator or not here and also its stride length
        int s;
        if(ctx.simplifiedAssignment().expressionName().size()>1){
            s = table.table.get(ctx.simplifiedAssignment().expressionName(1).Identifier().getText()).intv.val;
        }
        else{
            s= Integer.parseInt(ctx.simplifiedAssignment().IntegerLiteral().getText());
        }
        table.table.get(ctx.simplifiedAssignment().expressionName(0).Identifier().getText()).intv.updateStride(s);
        table.table.get(ctx.simplifiedAssignment().expressionName(0).Identifier().getText()).intv.updateSteps();
        
    }

    @Override
    public void exitSimplifiedAssignment(LoopNestParser.SimplifiedAssignmentContext ctx) {
    }

    @Override
    public void exitArrayAccess(LoopNestParser.ArrayAccessContext ctx) {
        // do it
        ArrVariable v = table.table.get(ctx.expressionName(0).Identifier().getText()).arrv;
        HashMap<String,Integer> var_order = new HashMap<String,Integer>();
        for(int i=0;i<v.dim.length;i++){
            var_order.put(ctx.expressionName(i+1).Identifier().getText(),i);
        }
        v.update_map(table.nest_lvl, var_order);
        table.table.get(ctx.expressionName(0).Identifier().getText()).arrv = v;
        // ArrayList<HashMap<String,Integer>> i1 = table.table.get(ctx.expressionName(0).Identifier().getText()).arrv.nest_order.get(2);
        // if(Objects.nonNull(i1)) System.out.println(i1.size());
    }

    @Override
    public void exitArrayAccess_lfno_primary(LoopNestParser.ArrayAccess_lfno_primaryContext ctx) {
        //do it
        ArrVariable v = table.table.get(ctx.expressionName(0).Identifier().getText()).arrv;
        HashMap<String,Integer> var_order = new HashMap<String,Integer>();
        for(int i=0;i<v.dim.length;i++){
            var_order.put(ctx.expressionName(i+1).Identifier().getText(),i);
        }
        v.update_map(table.nest_lvl, var_order);
        table.table.get(ctx.expressionName(0).Identifier().getText()).arrv = v;
        // ArrayList<HashMap<String,Integer>> i1 = table.table.get(ctx.expressionName(0).Identifier().getText()).arrv.nest_order.get(2);
        // if(Objects.nonNull(i1)) System.out.println(i1.size());
    }

}
