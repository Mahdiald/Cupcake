package dk.ek.cupcake;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class App {
    static final String URL = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/cupcakes");
    static final String USER = System.getenv().getOrDefault("DB_USER", "postgres");
    static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "postgres");
    static final TemplateEngine templates = new TemplateEngine();
    static final class Item {
        private final long id; private final String name; private final BigDecimal price;
        Item(long id, String name, BigDecimal price) { this.id=id; this.name=name; this.price=price; }
        public long getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
        long id() { return id; } String name() { return name; } BigDecimal price() { return price; }
    }
    static final class Line {
        private final long bottomId,toppingId; private final String bottom,topping;
        private final int quantity; private final BigDecimal unitPrice;
        Line(long bottomId,long toppingId,String bottom,String topping,int quantity,BigDecimal unitPrice) {
            this.bottomId=bottomId;this.toppingId=toppingId;this.bottom=bottom;this.topping=topping;this.quantity=quantity;this.unitPrice=unitPrice;
        }
        public String getBottom() { return bottom; }
        public String getTopping() { return topping; }
        public int getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getTotal() { return total(); }
        long bottomId() { return bottomId; } long toppingId() { return toppingId; }
        int quantity() { return quantity; } BigDecimal unitPrice() { return unitPrice; }
        BigDecimal total() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }
    static Connection db() throws SQLException { return DriverManager.getConnection(URL, USER, PASSWORD); }
    static List<Item> products(String table) throws SQLException {
        if (!table.equals("bottoms") && !table.equals("toppings")) throw new IllegalArgumentException();
        List<Item> items = new ArrayList<>();
        try(Connection c=db(); PreparedStatement p=c.prepareStatement("SELECT id,name,price FROM " + table + " ORDER BY id"); ResultSet r=p.executeQuery()) {
            while(r.next()) items.add(new Item(r.getLong("id"),r.getString("name"),r.getBigDecimal("price")));
        }
        return items;
    }
    static Item item(String table, long id) throws SQLException {
        if (!table.equals("bottoms") && !table.equals("toppings")) throw new IllegalArgumentException();
        try(Connection c=db(); PreparedStatement p=c.prepareStatement("SELECT id,name,price FROM " + table + " WHERE id=?")) {
            p.setLong(1,id);
            try(ResultSet r=p.executeQuery()) { if(r.next()) return new Item(r.getLong(1),r.getString(2),r.getBigDecimal(3)); }
        }
        throw new IllegalArgumentException("Ugyldigt produkt");
    }
    static Long userId(Context ctx) { return ctx.sessionAttribute("uid"); }
    static String role(Context ctx) { return ctx.sessionAttribute("role"); }
    static void requireLogin(Context ctx) { if(userId(ctx)==null) throw new IllegalStateException("Log ind først"); }
    static void requireAdmin(Context ctx) { if(!"ADMIN".equals(role(ctx))) throw new SecurityException("Kun administrator"); }
    @SuppressWarnings("unchecked")
    static List<Line> cart(Context ctx) {
        List<Line> cart=ctx.sessionAttribute("cart");
        if(cart==null) { cart=new ArrayList<>(); ctx.sessionAttribute("cart",cart); }
        return cart;
    }
    static void page(Context ctx,String view,Map<String,Object> data) {
        org.thymeleaf.context.Context t=new org.thymeleaf.context.Context(new Locale("da"));
        Map<String,Object> m=new HashMap<>(data);
        m.put("email",ctx.sessionAttribute("email")); m.put("role",role(ctx));
        m.put("cartSize",cart(ctx).stream().mapToInt(Line::quantity).sum());
        // Hent altid saldo fra databasen, så nye betalinger vises straks.
        if (userId(ctx) != null) {
            try (Connection c = db()) {
                m.put("balance", balance(c, userId(ctx)));
            } catch (SQLException e) {
                throw new IllegalStateException("Kunne ikke hente kontosaldo", e);
            }
        }
        t.setVariables(m); ctx.contentType("text/html; charset=UTF-8").result(templates.process(view,t));
    }
    static String param(Context ctx,String name) { String s=ctx.formParam(name); return s==null?"":s.trim(); }
    static void redirectError(Context ctx,String message) { ctx.sessionAttribute("error",message); ctx.redirect("/"); }
    static BigDecimal balance(Connection c,long id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT balance FROM users WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Ukendt bruger"); return r.getBigDecimal(1);}}
    }
    static void home(Context ctx) throws SQLException {
        String error=ctx.sessionAttribute("error"); ctx.sessionAttribute("error",null);
        page(ctx,"index",Map.of("bottoms",products("bottoms"),"toppings",products("toppings"),"error",error==null?"":error));
    }
    public static void main(String[] args) {
        ClassLoaderTemplateResolver resolver=new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");resolver.setCharacterEncoding("UTF-8");resolver.setCacheable(false);
        templates.setTemplateResolver(resolver);
        Javalin app=Javalin.create(cfg -> cfg.staticFiles.add("/public"));
        app.exception(Exception.class,(e,ctx)-> {e.printStackTrace(); ctx.status(e instanceof SecurityException?403:400); page(ctx,"message",Map.of("message",e.getMessage()==null?"Der skete en fejl":e.getMessage()));});
        app.get("/",App::home);
        app.get("/register",ctx->page(ctx,"auth",Map.of("registration",true)));
        app.get("/login",ctx->page(ctx,"auth",Map.of("registration",false)));
        app.post("/register",ctx->{
            String email=param(ctx,"email").toLowerCase(Locale.ROOT), pass=param(ctx,"password");
            if(!email.contains("@")||email.length()>255||pass.length()<8)throw new IllegalArgumentException("Indtast en gyldig email og mindst 8 tegn som kodeord");
            try(Connection c=db();PreparedStatement p=c.prepareStatement("INSERT INTO users(email,password_hash) VALUES (?,?) RETURNING id")) {
                p.setString(1,email);p.setString(2,BCrypt.hashpw(pass,BCrypt.gensalt(12)));
                try(ResultSet r=p.executeQuery()){r.next();ctx.req().changeSessionId();ctx.sessionAttribute("uid",r.getLong(1));ctx.sessionAttribute("email",email);ctx.sessionAttribute("role","CUSTOMER");}
            } catch(SQLException e){if("23505".equals(e.getSQLState()))throw new IllegalArgumentException("Email findes allerede");throw e;}
            ctx.redirect("/");
        });
        app.post("/login",ctx->{
            String email=param(ctx,"email").toLowerCase(Locale.ROOT),pass=param(ctx,"password");
            try(Connection c=db();PreparedStatement p=c.prepareStatement("SELECT id,password_hash,role FROM users WHERE email=?")) {
                p.setString(1,email);try(ResultSet r=p.executeQuery()){
                    if(!r.next()||!BCrypt.checkpw(pass,r.getString("password_hash")))throw new IllegalArgumentException("Forkert email eller kodeord");
                    ctx.req().changeSessionId();ctx.sessionAttribute("uid",r.getLong("id"));ctx.sessionAttribute("email",email);ctx.sessionAttribute("role",r.getString("role"));
                }
            }ctx.redirect("/");
        });
        app.post("/logout",ctx->{ctx.req().getSession().invalidate();ctx.redirect("/");});
        app.post("/cart/add",ctx->{
            long bottom=Long.parseLong(param(ctx,"bottom")),top=Long.parseLong(param(ctx,"topping"));
            int qty=Integer.parseInt(param(ctx,"quantity"));if(qty<1||qty>100)throw new IllegalArgumentException("Antal skal være 1-100");
            Item b=item("bottoms",bottom),t=item("toppings",top);
            List<Line> lines=cart(ctx);lines.add(new Line(b.id(),t.id(),b.name(),t.name(),qty,b.price().add(t.price())));
            ctx.redirect("/cart");
        });
        app.get("/cart",ctx->{ List<Line> lines=cart(ctx);page(ctx,"cart",Map.of("lines",lines,"total",lines.stream().map(Line::total).reduce(BigDecimal.ZERO,BigDecimal::add))); });
        app.post("/cart/remove",ctx->{int index=Integer.parseInt(param(ctx,"index"));List<Line> lines=cart(ctx);if(index<0||index>=lines.size())throw new IllegalArgumentException("Ugyldig ordrelinje");lines.remove(index);ctx.redirect("/cart");});
        app.post("/checkout",ctx->{
            requireLogin(ctx);List<Line> lines=cart(ctx);if(lines.isEmpty())throw new IllegalArgumentException("Din kurv er tom");
            BigDecimal total=lines.stream().map(Line::total).reduce(BigDecimal.ZERO,BigDecimal::add);
            try(Connection c=db()) {
                c.setAutoCommit(false);
                try {
                    try(PreparedStatement p=c.prepareStatement("UPDATE users SET balance=balance-? WHERE id=? AND balance>=?")){
                        p.setBigDecimal(1,total);p.setLong(2,userId(ctx));p.setBigDecimal(3,total);
                        if(p.executeUpdate()!=1)throw new IllegalArgumentException("Ikke nok penge på kontoen. Bed administrator om at tilføje saldo i pgAdmin.");
                    }
                    long orderId;
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO orders(user_id,total) VALUES (?,?) RETURNING id")){
                        p.setLong(1,userId(ctx));p.setBigDecimal(2,total);
                        try(ResultSet r=p.executeQuery()){r.next();orderId=r.getLong(1);}
                    }
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO order_lines(order_id,bottom_id,topping_id,quantity,unit_price) VALUES (?,?,?,?,?)")){
                        for(Line l:lines){p.setLong(1,orderId);p.setLong(2,l.bottomId());p.setLong(3,l.toppingId());p.setInt(4,l.quantity());p.setBigDecimal(5,l.unitPrice());p.addBatch();}p.executeBatch();
                    }
                    c.commit();
                }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            }
            ctx.sessionAttribute("cart",new ArrayList<Line>());ctx.redirect("/orders");
        });
        app.get("/account",ctx->{
            requireLogin(ctx);
            List<Map<String,Object>> orders = new ArrayList<>();
            try (Connection c=db(); PreparedStatement p=c.prepareStatement(
                    "SELECT id,created_at,total FROM orders WHERE user_id=? ORDER BY created_at DESC,id DESC")) {
                p.setLong(1,userId(ctx));
                try (ResultSet r=p.executeQuery()) {
                    while (r.next()) {
                        orders.add(Map.of("id",r.getLong("id"),
                            "created",r.getTimestamp("created_at"),
                            "total",r.getBigDecimal("total")));
                    }
                }
            }
            page(ctx,"account",Map.of("orders",orders));
        });
        app.get("/orders",ctx->{requireLogin(ctx);List<Map<String,Object>> rows=new ArrayList<>();
            try(Connection c=db();PreparedStatement p=c.prepareStatement("SELECT id,created_at,total FROM orders WHERE user_id=? ORDER BY id DESC")){
                p.setLong(1,userId(ctx));try(ResultSet r=p.executeQuery()){while(r.next())rows.add(Map.of("id",r.getLong(1),"created",r.getTimestamp(2),"total",r.getBigDecimal(3)));}
            }page(ctx,"orders",Map.of("orders",rows));
        });
        app.get("/admin",ctx->{requireAdmin(ctx);List<Map<String,Object>> orders=new ArrayList<>(),users=new ArrayList<>();
            try(Connection c=db()){
                try(PreparedStatement p=c.prepareStatement("SELECT o.id,o.created_at,o.total,u.email FROM orders o JOIN users u ON u.id=o.user_id ORDER BY o.id DESC");ResultSet r=p.executeQuery()){
                    while(r.next())orders.add(Map.of("id",r.getLong(1),"created",r.getTimestamp(2),"total",r.getBigDecimal(3),"email",r.getString(4)));
                }
                try(PreparedStatement p=c.prepareStatement("SELECT u.id,u.email,u.balance,COUNT(o.id) order_count FROM users u LEFT JOIN orders o ON o.user_id=u.id GROUP BY u.id ORDER BY u.id");ResultSet r=p.executeQuery()){
                    while(r.next())users.add(Map.of("id",r.getLong(1),"email",r.getString(2),"balance",r.getBigDecimal(3),"orderCount",r.getLong(4)));
                }
            }page(ctx,"admin",Map.of("orders",orders,"users",users));
        });
        app.post("/admin/orders/delete",ctx->{requireAdmin(ctx);long id=Long.parseLong(param(ctx,"orderId"));
            try(Connection c=db();PreparedStatement p=c.prepareStatement("DELETE FROM orders WHERE id=?")){p.setLong(1,id);p.executeUpdate();}
            ctx.redirect("/admin");
        });
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","7070"));
        app.start(port);
    }
}
