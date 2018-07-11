package org.javarosa.xpath.expr;

import static org.javarosa.core.model.instance.TreeReference.CONTEXT_ABSOLUTE;
import static org.javarosa.core.model.instance.TreeReference.REF_ABSOLUTE;
import static org.javarosa.test.utils.ResourcePathHelper.r;

import java.util.Arrays;
import java.util.List;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.test.FormParseInit;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;

class Scenario {
    private final FormDef formDef;
    private final FormEntryController formEntryController;

    private Scenario(FormDef formDef, FormEntryController formEntryController) {
        this.formDef = formDef;
        this.formEntryController = formEntryController;
    }

    static Scenario init(String formFileName) {
        FormParseInit fpi = new FormParseInit(r(formFileName));
        FormDef formDef = fpi.getFormDef();
        formDef.initialize(true, new InstanceInitializationFactory());
        FormEntryModel formEntryModel = new FormEntryModel(formDef);
        FormEntryController formEntryController = new FormEntryController(formEntryModel);
        return new Scenario(formDef, formEntryController);
    }

    Scenario answer(String xpath, String value) {
        FormIndex index = getIndexOf(xpath);
        formEntryController.answerQuestion(index, new StringData(value), true);
        return this;
    }

    private TreeReference absoluteRef(String xpath) {
        TreeReference tr = new TreeReference();
        tr.setRefLevel(REF_ABSOLUTE);
        tr.setContext(CONTEXT_ABSOLUTE);
        tr.setInstanceName(null);
        Arrays.stream(xpath.split("/"))
            .filter(s -> !s.isEmpty())
            .forEach(s -> {
                int multiplicity = s.contains("[") ? Integer.parseInt(s.substring(s.indexOf("[") + 1, s.indexOf("]"))) : 0;
                String part = s.contains("[") ? s.substring(0, s.indexOf("[")) : s;
                tr.add(part, multiplicity);
            });
        return tr;
    }

    private FormIndex getIndexOf(String xpath) {
        // TODO Could this be resolved at initialization time and stored in a map of treereferences and formindexes?
        TreeReference ref = absoluteRef(xpath);
        formEntryController.jumpToIndex(FormIndex.createBeginningOfFormIndex());
        FormEntryModel model = formEntryController.getModel();
        FormIndex index = model.getFormIndex();
        do {
            if (index.getReference() != null && index.getReference().equals(ref))
                return index;
            index = model.incrementIndex(index);
        } while (index.isInForm());
        throw new IllegalArgumentException("Reference " + ref + " not found");
    }

    @SuppressWarnings("unchecked")
    <T extends IAnswerData> T answerOf(String xpath) {
        TreeElement root = (TreeElement) formDef.getMainInstance().getRoot().getParent();
        TreeElement resolve = resolve(root, xpath.startsWith("/") ? xpath.substring(1) : xpath);
        return (T) resolve.getValue();
    }

    static TreeElement resolve(TreeElement current, String xpath) {
        List<String> parts = Arrays.asList(xpath.split("/"));
        String firstPart = parts.get(0);
        String name = firstPart.contains("[") ? firstPart.substring(0, firstPart.indexOf("[")) : firstPart;
        int multiplicity = firstPart.contains("[") ? Integer.parseInt(firstPart.substring(firstPart.indexOf("[") + 1, firstPart.indexOf("]"))) : 0;
        TreeElement nextElement = current.getChild(name, multiplicity);
        return parts.size() > 1 ? resolve(nextElement, shift(xpath)) : nextElement;
    }

    private static String shift(String xpath) {
        List<String> parts2 = Arrays.asList(xpath.split("/"));
        return String.join("/", parts2.subList(1, parts2.size()));
    }

    List<SelectChoice> choicesOf(String xpath) {
        QuestionDef control = formEntryController.getModel().getQuestionPrompt(getIndexOf(xpath)).getQuestion();
        formDef.populateDynamicChoices(control.getDynamicChoices(), (TreeReference) control.getBind().getReference());
        return control.getChoices() == null
            ? control.getDynamicChoices().getChoices()
            : control.getChoices();
    }
}
