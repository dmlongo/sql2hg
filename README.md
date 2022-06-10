# sql2hg
Simple converter from SQL queries to hypergraphs.

At the moment it only supports queries of the form:<br>
SELECT *<br>
FROM <i>tab1, tab2, ...</i><br>
WHERE <i>tab_i.attr_z = tab_j.attr_w</i> AND <i>...</i>
