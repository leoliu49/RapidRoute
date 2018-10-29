import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.edif.EDIFCellInst;

public class RegisterComponent {

    /*
     * Register confined to a single site, which are building blocks to the ComplexRegister class
     */

    private String name = null;

    private int type;
    private String siteName;
    private int bitWidth;

    private ModuleInst moduleInstance;

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

    public void setName(String name) {
        this.name = name;
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

    public ModuleInst getModuleInstance() {
        return moduleInstance;
    }

    public EDIFCellInst getCellInstance() {
        return moduleInstance.getCellInst();
    }

    public void setModuleInstance(ModuleInst moduleInstance) {
        this.moduleInstance = moduleInstance;
    }

    public String getInPIPName(int index) {
        return ComplexRegister.getInPIPName(type, index);
    }

    public String getOutPIPName(int index) {
        return ComplexRegister.getOutPIPName(type, index);
    }

    @Override
    public String toString() {
        return name + ComplexRegister.typeToRegModuleMap.get(type).toString();
    }
}
