import java.util.ArrayList;
import java.util.List;

public class ParseTreeNode {
    String name;
    Token token;
    List<ParseTreeNode> children;

    public ParseTreeNode(String name) {
        this.name = name;
        this.token = null;
        this.children = new ArrayList<>();
    }

    public ParseTreeNode(String name, Token token) {
        this.name = name;
        this.token = token;
        this.children = new ArrayList<>();
    }

    public void addChild(ParseTreeNode child) {
        this.children.add(child);
    }
}