import java.sql.*;
public class Peek {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url=System.getenv("DB_URL")!=null?System.getenv("DB_URL"):"jdbc:mysql://localhost:3306/astralib";
    String user=System.getenv("DB_USERNAME")!=null?System.getenv("DB_USERNAME"):"root";
    String pw=System.getenv("DB_PASSWORD")!=null?System.getenv("DB_PASSWORD"):"";
    try (Connection c=DriverManager.getConnection(url,user,pw)) {
      try (Statement s=c.createStatement()) {
        ResultSet r=s.executeQuery("select chunk_id, left(content,300), embedding_json from vector_records where title like 'Think Java%' and section like '%Recursion%' limit 1");
        while (r.next()) {
          String emb=r.getString(3);
          System.out.println("ID="+r.getString(1));
          System.out.println("TEXT="+r.getString(2).replaceAll("\s+"," "));
          System.out.println("EMB_LEN="+(emb==null?0:emb.length()));
          java.nio.file.Files.writeString(java.nio.file.Path.of(".tmp/db/emb.json"), emb);
          java.nio.file.Files.writeString(java.nio.file.Path.of(".tmp/db/text.txt"), r.getString(2));
        }
      }
    }
  }
}
