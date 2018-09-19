import com.xilinx.rapidwright.design.ModuleInstance;
import com.xilinx.rapidwright.edif.EDIFCellInstance;

public class RegisterComponent {

    /*
     * Register confined to a single site, which are building blocks to the ComplexRegister class
     */

    private String name = null;

    private int type;
    private String siteName;
    private int bitWidth;

    private ModuleInstance moduleInstance;

    public RegisterComponent(String name, int type, String siteName) {
        this.name = name;
        this.type = type;
        this.siteName = siteName;

        bitWidth = ComplexRegister.typeToRegModuleMap.get(type).getBitWidth();
    }

    public RegisterComponent(int type, String siteName) {
        this.type = type;
        this.siteName = siteName;

        bitWidth = ComplexRegister.typeToRegModuleMap.get(type).getBitWidth();
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

    public int getBitWidth() {
        return bitWidth;
    }

    public ModuleInstance getModuleInstance() {
        return moduleInstance;
    }

    public EDIFCellInstance getCellInstance() {
        return moduleInstance.getCellInstance();
    }

    public void setModuleInstance(ModuleInstance moduleInstance) {
        this.moduleInstance = moduleInstance;
    }
}
