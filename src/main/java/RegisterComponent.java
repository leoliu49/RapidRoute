public class RegisterComponent {

    /*
     * Register confined to a single site, which are building blocks to the ComplexRegister class
     */

    private String name;

    private int type;
    private String siteName;


    public RegisterComponent(String name, int type, String siteName) {
        this.name = name;
        this.type = type;
        this.siteName = siteName;
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public String getSiteName() {
        return siteName;
    }
}
