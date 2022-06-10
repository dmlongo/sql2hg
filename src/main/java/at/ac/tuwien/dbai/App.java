package at.ac.tuwien.dbai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Hello world!
 */
public final class App {
    private App() {
    }

    /**
     * Says hello to the world.
     * 
     * @param args The arguments of the program.
     * @throws JSQLParserException
     * @throws IOException
     */
    public static void main(String[] args) throws JSQLParserException, IOException {
        if (args.length != 1) {
            System.err.println("Usage: sql2hg <query>");
            System.exit(-1);
        }

        String query = new String(Files.readAllBytes(Paths.get(args[0])));
        Statement stmt = CCJSqlParserUtil.parse(query);
        System.out.println(stmt);
        System.out.println();

        Select selectStmt = (Select) stmt;
        TablesNamesFinder tnf = new TablesNamesFinder();
        List<String> tableList = tnf.getTableList(selectStmt);
        System.out.println("Tables: " + tableList);

        JoinFinder jf = new JoinFinder();
        List<Equality> joinList = jf.getJoinList(selectStmt);
        System.out.println("Joins: " + joinList);

        HypergraphBuilder builder = new HypergraphBuilder();
        for (String t : tableList) {
            builder.buildEdge(t);
        }
        for (Equality eq : joinList) {
            builder.buildEdge(eq.leftTable, eq.leftCol);
            builder.buildEdge(eq.rightTable, eq.rightCol);
            builder.buildJoin(eq);
        }

        List<String> hg = builder.makeHypergraph();
        System.out.println("\nHypergraph:\n" + hg);

        String hgFile = new File(args[0]).getAbsolutePath();
        int startIdx = hgFile.lastIndexOf(File.separator) + 1;
        int endIdx = hgFile.lastIndexOf('.');
        String fileBaseName = hgFile.substring(startIdx, endIdx);
        hgFile = fileBaseName + ".hg";
        writeToFile(hgFile, hg);

        List<String> map = builder.getMapping();
        System.out.println("\nMapping:\n" + map);

        String mapFile = fileBaseName + ".map";
        writeToFile(mapFile, map);
    }

    public static void writeToFile(String filename, List<String> content) throws IOException {
        Path filePath = Paths.get(filename);
        if (!Files.exists(filePath))
            Files.createFile(filePath);
        Files.write(filePath, content, StandardCharsets.UTF_8);
    }

    static class Equality {
        String leftTable;
        String leftCol;
        String rightTable;
        String rightCol;

        public Equality(Column left, Column right) {
            this.leftTable = left.getTable().getFullyQualifiedName();
            this.leftCol = left.getColumnName();
            this.rightTable = right.getTable().getFullyQualifiedName();
            this.rightCol = right.getColumnName();
        }

        @Override
        public String toString() {
            return leftTable + "." + leftCol + " = " + rightTable + "." + rightCol;
        }
    }

    static class JoinFinder extends QueryVisitorUnsupportedAdapter {
        LinkedList<Equality> myJoins;

        public List<Equality> getJoinList(Statement stmt) {
            myJoins = new LinkedList<>();
            stmt.accept(this);
            return myJoins;
        }

        @Override
        public void visit(Select select) {
            if (select.getWithItemsList() != null) {
                for (WithItem withItem : select.getWithItemsList()) {
                    withItem.accept(this);
                }
            }
            select.getSelectBody().accept(this);
        }

        @Override
        public void visit(PlainSelect plainSelect) {
            if (plainSelect.getSelectItems() != null) {
                for (SelectItem item : plainSelect.getSelectItems()) {
                    item.accept(this);
                }
            }

            if (plainSelect.getFromItem() != null) {
                plainSelect.getFromItem().accept(this);
            }

            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    join.getRightItem().accept(this);
                }
            }
            if (plainSelect.getWhere() != null) {
                plainSelect.getWhere().accept(this);
            }

            if (plainSelect.getHaving() != null) {
                plainSelect.getHaving().accept(this);
            }

            if (plainSelect.getOracleHierarchical() != null) {
                plainSelect.getOracleHierarchical().accept(this);
            }
        }

        @Override
        public void visit(AllColumns allColumns) {
            // nothing to do for * in SELECT
        }

        @Override
        public void visit(Table tableName) {
            // nothing to do for table in FROM
        }

        @Override
        public void visit(AndExpression andExpression) {
            visitBinaryExpression(andExpression);
        }

        public void visitBinaryExpression(BinaryExpression binaryExpression) {
            binaryExpression.getLeftExpression().accept(this);
            binaryExpression.getRightExpression().accept(this);
        }

        @Override
        public void visit(EqualsTo equalsTo) {
            Expression left = equalsTo.getLeftExpression();
            Expression right = equalsTo.getRightExpression();
            if (left instanceof Column && right instanceof Column) {
                myJoins.add(new Equality((Column) left, (Column) right));
            } else {
                super.visit(equalsTo);
            }
        }
    }
}