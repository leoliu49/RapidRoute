public class RegisterComponent {

    /*
     * Register confined to a single site, which are building blocks to the ComplexRegister class
     */

    private String name = null;

    private int type;
    private String siteName;

    public RegisterComponent(String name, int type, String siteName) {
        this.name = name;
        this.type = type;
        this.siteName = siteName;
    }

    public RegisterComponent(int type, String siteName) {
        this.type = type;
        this.siteName = siteName;
    }

    public String getName() {
        return name;
    }

    public boolean hasName() {
        return !(name == null);
    }

    public int getType() {
        return type;
    }

    public String getSiteName() {
        return siteName;
    }
}
