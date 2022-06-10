package at.ac.tuwien.dbai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jgrapht.alg.util.UnionFind;

import at.ac.tuwien.dbai.App.Equality;

public class HypergraphBuilder {

    private HashMap<String, HashSet<String>> hg;
    private int nextVar;
    private HashMap<String, String> colToVar;
    private UnionFind<String> vars;

    public HypergraphBuilder() {
        hg = new HashMap<>();
        nextVar = 0;
        colToVar = new HashMap<>();
        vars = new UnionFind<>(new HashSet<>());
    }

    public void buildEdge(String table) {
        hg.put(table, new HashSet<>());
    }

    public void buildEdge(String table, String col) {
        hg.get(table).add(col);

        String attr = stringify(table, col);
        colToVar.computeIfAbsent(attr, k -> {
            String vert = "v" + nextVar++;
            vars.addElement(vert);
            return vert;
        });
    }

    public void buildJoin(Equality eq) {
        if (!hg.containsKey(eq.leftTable) || !hg.containsKey(eq.rightTable)) {
            throw new IllegalArgumentException("missing tables in FROM: " + eq.leftTable + " or " + eq.rightTable);
        }
        if (!hg.get(eq.leftTable).contains(eq.leftCol) || !hg.get(eq.rightTable).contains(eq.rightCol)) {
            throw new IllegalArgumentException("missing attribute: " + eq.leftCol + " or " + eq.rightCol);
        }
        String vert1 = colToVar.get(stringify(eq.leftTable, eq.leftCol));
        String vert2 = colToVar.get(stringify(eq.rightTable, eq.rightCol));
        vars.union(vert1, vert2);
    }

    private String stringify(String table, String col) {
        return table + "." + col;
    }

    public List<String> makeHypergraph() {
        LinkedList<String> out = new LinkedList<>();
        for (Entry<String, HashSet<String>> edge : hg.entrySet()) {
            StringBuilder sb = new StringBuilder(100);
            sb.append(edge.getKey());
            sb.append('(');
            boolean delete = false;
            for (String v : edge.getValue()) {
                delete = true;

                String vert = colToVar.get(stringify(edge.getKey(), v));
                sb.append(vars.find(vert));
                sb.append(',');
            }
            if (delete) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append(')');
            out.add(sb.toString());
        }
        return out;
    }

    public List<String> getMapping() {
        HashMap<String, List<String>> varToCol = new HashMap<>();
        for (Map.Entry<String, String> entry : colToVar.entrySet()) {
            String col = entry.getKey();
            String v = vars.find(entry.getValue());
            varToCol.computeIfAbsent(v, k -> new LinkedList<>()).add(col);
        }

        ArrayList<String> lines = new ArrayList<>(varToCol.size());
        for (Map.Entry<String, List<String>> entry : varToCol.entrySet()) {
            StringBuilder sb = new StringBuilder(100);
            sb.append(entry.getKey());
            sb.append('=');
            Iterator<String> it = entry.getValue().iterator();
            while (it.hasNext()) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            lines.add(sb.toString());
        }
        return lines;
    }

}
