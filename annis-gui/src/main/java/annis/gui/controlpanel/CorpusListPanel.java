/*
 * Copyright 2011 Corpuslinguistic working group Humboldt University Berlin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package annis.gui.controlpanel;

import annis.gui.ExampleQueriesPanel;
import annis.gui.CorpusBrowserPanel;
import annis.gui.MetaDataPanel;
import annis.libgui.Helper;
import annis.security.AnnisUserConfig;
import annis.libgui.CorpusSet;
import annis.libgui.InstanceConfig;
import annis.gui.QueryController;
import annis.gui.SearchUI;
import annis.service.objects.AnnisCorpus;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.vaadin.data.Item;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanContainer;
import com.vaadin.data.util.DefaultItemSorter;
import com.vaadin.data.util.filter.SimpleStringFilter;
import com.vaadin.event.Action;
import com.vaadin.event.FieldEvents;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.server.ExternalResource;
import static com.vaadin.server.Sizeable.UNITS_EM;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.AbstractSelect;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.BaseTheme;
import com.vaadin.ui.themes.ChameleonTheme;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thomas
 */
public class CorpusListPanel extends VerticalLayout implements
  AbstractSelect.NewItemHandler, Action.Handler
{

  private static final org.slf4j.Logger log = LoggerFactory.
    getLogger(CorpusListPanel.class);

  private static final ThemeResource INFO_ICON = new ThemeResource("info.gif");

  private static final ThemeResource DOC_ICON = new ThemeResource(
    "document_ico.png");

  public static final String ALL_CORPORA = "All";

  // holds the panels of auto generated queries
  private final ExampleQueriesPanel autoGenQueries;

  private SearchUI ui;

  public enum ActionType
  {

    Add, Remove

  };
  private BeanContainer<String, AnnisCorpus> corpusContainer;

  private Table tblCorpora;

  private QueryController controller;

  private ComboBox cbSelection;

  private TextField txtFilter;

  private transient AnnisUserConfig userConfig;

  private List<AnnisCorpus> allCorpora = new LinkedList<AnnisCorpus>();

  private InstanceConfig instanceConfig;
  
  public CorpusListPanel(final QueryController controller,
    InstanceConfig instanceConfig, ExampleQueriesPanel autoGenQueries,
    SearchUI ui)
  {
    this.controller = controller;
    this.instanceConfig = instanceConfig;
    this.autoGenQueries = autoGenQueries;
    this.ui = ui;

    final CorpusListPanel finalThis = this;

    setSizeFull();

    HorizontalLayout selectionLayout = new HorizontalLayout();
    selectionLayout.setWidth("100%");
    selectionLayout.setHeight("-1px");

    Label lblVisible = new Label("Visible: ");
    lblVisible.setSizeUndefined();
    selectionLayout.addComponent(lblVisible);

    cbSelection = new ComboBox();
    cbSelection.setDescription("Choose corpus selection set");
    cbSelection.setWidth("100%");
    cbSelection.setHeight("-1px");
    cbSelection.setInputPrompt("Add new corpus selection set");
    cbSelection.setNullSelectionAllowed(false);
    cbSelection.setNewItemsAllowed(true);
    cbSelection.setNewItemHandler((AbstractSelect.NewItemHandler) this);
    cbSelection.setImmediate(true);
    cbSelection.addValueChangeListener(new ValueChangeListener()
    {
      @Override
      public void valueChange(ValueChangeEvent event)
      {
        updateCorpusTable();
        updateAutoGeneratedQueriesPanel();
      }
    });

    selectionLayout.addComponent(cbSelection);
    selectionLayout.setExpandRatio(cbSelection, 1.0f);
    selectionLayout.setSpacing(true);
    selectionLayout.setComponentAlignment(cbSelection, Alignment.MIDDLE_RIGHT);
    selectionLayout.setComponentAlignment(lblVisible, Alignment.MIDDLE_LEFT);

    addComponent(selectionLayout);

    txtFilter = new TextField();
    txtFilter.setInputPrompt("Filter");
    txtFilter.setImmediate(true);
    txtFilter.setTextChangeTimeout(500);
    txtFilter.addTextChangeListener(new FieldEvents.TextChangeListener()
    {
      @Override
      public void textChange(FieldEvents.TextChangeEvent event)
      {
        corpusContainer.removeAllContainerFilters();
        if (event.getText() != null && !event.getText().isEmpty())
        {
          Set<String> selectedIDs = getSelectedCorpora();

          corpusContainer.addContainerFilter(
            new SimpleStringFilter("name", event.getText(), true, false));
          // select the first item
          List<String> filteredIDs = corpusContainer.getItemIds();

          Set<String> selectedAndFiltered = new HashSet<String>(selectedIDs);
          selectedAndFiltered.retainAll(filteredIDs);

          Set<String> selectedAndOutsideFilter = new HashSet<String>(selectedIDs);
          selectedAndOutsideFilter.removeAll(filteredIDs);

          for (String id : selectedAndOutsideFilter)
          {
            tblCorpora.unselect(id);
          }

          if (selectedAndFiltered.isEmpty() && !filteredIDs.isEmpty())
          {
            for (String id : selectedIDs)
            {
              tblCorpora.unselect(id);
            }
            tblCorpora.select(filteredIDs.get(0));
          }
        }
      }
    });
    txtFilter.setWidth("100%");
    txtFilter.setHeight("-1px");
    addComponent(txtFilter);

    tblCorpora = new Table();
    
    addComponent(tblCorpora);

    corpusContainer = new BeanContainer<String, AnnisCorpus>(AnnisCorpus.class);
    corpusContainer.setBeanIdProperty("name");
    corpusContainer.setItemSorter(new CorpusSorter());


    tblCorpora.setContainerDataSource(corpusContainer);

    tblCorpora.addGeneratedColumn("info", new InfoGenerator());
    tblCorpora.addGeneratedColumn("docs", new DocLinkGenerator());

    tblCorpora.setVisibleColumns("name", "textCount", "tokenCount", "info",
      "docs");
    tblCorpora.setColumnHeaders("Name", "Texts", "Tokens", "", "");
    tblCorpora.setHeight("100%");
    tblCorpora.setWidth("100%");
    tblCorpora.setSelectable(true);
    tblCorpora.setMultiSelect(true);
    tblCorpora.setNullSelectionAllowed(false);
    tblCorpora.setColumnExpandRatio("name", 0.6f);
    tblCorpora.setColumnExpandRatio("textCount", 0.15f);
    tblCorpora.setColumnExpandRatio("tokenCount", 0.25f);
    tblCorpora.setColumnWidth("info", 19);
    
    tblCorpora.addActionHandler((Action.Handler) this);
    tblCorpora.setImmediate(true);
    tblCorpora.addItemClickListener(new ItemClickEvent.ItemClickListener()
    {
      @Override
      public void itemClick(ItemClickEvent event)
      {
        Set selections = (Set) tblCorpora.getValue();
        if (selections.size() == 1
          && event.isCtrlKey() && tblCorpora.isSelected(event.getItemId()))
        {
          tblCorpora.setValue(null);
        }
      }
    });
    tblCorpora.setItemDescriptionGenerator(new TooltipGenerator());

    tblCorpora.addValueChangeListener(new CorpusTableChangedListener(finalThis));

    setExpandRatio(tblCorpora, 1.0f);

    Button btReload = new Button();
    btReload.addListener(new Button.ClickListener()
    {
      @Override
      public void buttonClick(ClickEvent event)
      {
        updateCorpusSetList(false);
        Notification.show("Reloaded corpus list",
          Notification.Type.HUMANIZED_MESSAGE);
      }
    });
    btReload.setIcon(new ThemeResource("tango-icons/16x16/view-refresh.png"));
    btReload.setDescription("Reload corpus list");
    btReload.addStyleName(ChameleonTheme.BUTTON_ICON_ONLY);

    selectionLayout.addComponent(btReload);
    selectionLayout.setComponentAlignment(btReload, Alignment.MIDDLE_RIGHT);

    tblCorpora.setSortContainerPropertyId("name");
    updateCorpusSetList(true);
  }

  public void updateCorpusSetList()
  {
    updateCorpusSetList(false);
  }

  private void updateCorpusSetList(boolean showLoginMessage)
  {
    if(ui != null)
    {
      ui.clearCorpusConfigCache();
    }
    
    if (queryServerForCorpusList() && userConfig != null)
    {
      if (VaadinSession.getCurrent().getAttribute(AnnisCorpus.class) == null)
      {
        if (showLoginMessage)
        {
          if (allCorpora.isEmpty())
          {
            Notification.show("No corpora found. Please login "
              + "(use button at upper right corner) to see more corpora.",
              Notification.Type.HUMANIZED_MESSAGE);
          }
          else if (Helper.getUser() == null)
          {
            Notification.
              show(
              "You can login (use button at upper right corner) to get access to more corpora",
              Notification.Type.TRAY_NOTIFICATION);
          }
        }
      }

      Object oldSelection = cbSelection.getValue();
      cbSelection.removeAllItems();
      cbSelection.addItem(ALL_CORPORA);

      List<CorpusSet> corpusSets = new LinkedList<CorpusSet>();
      if (instanceConfig != null && instanceConfig.getCorpusSets() != null)
      {
        corpusSets.addAll(instanceConfig.getCorpusSets());
      }

      if (userConfig.getCorpusSets() != null)
      {
        corpusSets.addAll(userConfig.getCorpusSets());
      }

      // add the corpus set names in sorted order
      TreeSet<String> corpusSetNames = new TreeSet<String>();
      for (CorpusSet cs : corpusSets)
      {
        corpusSetNames.add(cs.getName());
      }
      for (String s : corpusSetNames)
      {
        cbSelection.addItem(s);
      }

      // restore old selection or select the ALL corpus selection
      if (oldSelection != null && cbSelection.containsId(oldSelection))
      {
        cbSelection.select(oldSelection);
      }
      else
      {

        if (instanceConfig != null && instanceConfig.getDefaultCorpusSet() != null
          && instanceConfig.getDefaultCorpusSet().length() > 0)
        {
          cbSelection.select(instanceConfig.getDefaultCorpusSet());
        }
        else
        {
          cbSelection.select(ALL_CORPORA);
        }
      }

      updateCorpusTable();
      updateAutoGeneratedQueriesPanel();
    } // end if querying the server for corpus list was successful
  }

  private void updateCorpusTable()
  {
    corpusContainer.removeAllItems();
    String selectedCorpusSetName = (String) cbSelection.getValue();

    if (selectedCorpusSetName == null || ALL_CORPORA.equals(
      selectedCorpusSetName))
    {
      // add all corpora
      corpusContainer.addAll(allCorpora);
    }
    else if (userConfig != null)
    {
      CorpusSet selectedCS = null;

      // TODO: use map
      List<CorpusSet> corpusSets = new LinkedList<CorpusSet>();
      if (instanceConfig != null && instanceConfig.getCorpusSets() != null)
      {
        corpusSets.addAll(instanceConfig.getCorpusSets());
      }

      if (userConfig.getCorpusSets() != null)
      {
        corpusSets.addAll(userConfig.getCorpusSets());
      }

      for (CorpusSet cs : corpusSets)
      {
        if (cs.getName().equals(selectedCorpusSetName))
        {
          selectedCS = cs;
        }
      }
      if (selectedCS != null)
      {
        LinkedList<AnnisCorpus> shownCorpora = new LinkedList<AnnisCorpus>();
        for (AnnisCorpus c : allCorpora)
        {
          if (selectedCS.getCorpora().contains(c.getName()))
          {
            shownCorpora.add(c);
          }
        }
        corpusContainer.addAll(shownCorpora);
      }
    }
    tblCorpora.sort();
  }

  /**
   * Updates or initializes the panel, which holds the automatic generated
   * queries.
   */
  private void updateAutoGeneratedQueriesPanel()
  {
    Set<String> corpora = getSelectedCorpora();

    if (corpora.isEmpty())
    {
      corpora.addAll(corpusContainer.getItemIds());
    }
    autoGenQueries.setSelectedCorpusInBackground(corpora);
  }
  
  /**
   * Queries the web service and sets the {@link #allCorpora} and
   * {@link #userConfig} members.
   *
   * @return True if successful
   */
  private boolean queryServerForCorpusList()
  {
    try
    {
      loadFromRemote();

      WebResource rootRes = Helper.getAnnisWebResource();
      allCorpora = rootRes.path("query").path("corpora")
        .get(new AnnisCorpusListType());

      return true;
    }
    catch (ClientHandlerException ex)
    {
      log.error(null, ex);
      Notification.show("Service not available: " + ex.getLocalizedMessage(),
        Notification.Type.TRAY_NOTIFICATION);
    }
    catch (UniformInterfaceException ex)
    {
      if (ex.getResponse().getStatus() == Response.Status.UNAUTHORIZED.
        getStatusCode())
      {
        Notification.show("You are not authorized to get the corpus list.", ex.
          getMessage(), Notification.Type.WARNING_MESSAGE);
      }
      else
      {
        log.error(null, ex);
        Notification.show("Remote exception: " + ex.getLocalizedMessage(),
          Notification.Type.TRAY_NOTIFICATION);
      }
    }
    catch (Exception ex)
    {
      log.error(null, ex);
      Notification.show("Exception: " + ex.getLocalizedMessage(),
        Notification.Type.TRAY_NOTIFICATION);
    }
    return false;
  }

  private void loadFromRemote()
  {
    WebResource rootRes = Helper.getAnnisWebResource();
    // get the current corpus configuration
    this.userConfig = rootRes.path("admin").path("userconfig").
      get(AnnisUserConfig.class);
  }

  private void storeChangesRemote()
  {
    WebResource rootRes = Helper.getAnnisWebResource();
    // store the config on the server
    rootRes.path("admin").path("userconfig").post(this.userConfig);
  }

  @Override
  public void addNewItem(String newItemCaption)
  {
    if (!cbSelection.containsId(newItemCaption) && this.userConfig != null)
    {
      cbSelection.addItem(newItemCaption);
      cbSelection.setValue(newItemCaption);

      try
      {
        loadFromRemote();
        // add new corpus set to the config
        CorpusSet newSet = new CorpusSet();
        newSet.setName(newItemCaption);
        this.userConfig.getCorpusSets().add(newSet);
        // store the config on the server
        storeChangesRemote();

        // update everything else
        updateCorpusTable();
      }
      catch (ClientHandlerException ex)
      {
        log.error(
          "could not store new corpus set", ex);
        Notification.show("Could not store new corpus set: "
          + ex.getLocalizedMessage(), Type.WARNING_MESSAGE);
      }
      catch (UniformInterfaceException ex)
      {

        if (ex.getResponse().getStatus() == Response.Status.UNAUTHORIZED.
          getStatusCode())
        {
          log.error(ex.getLocalizedMessage());
          Notification.show("Not allowed",
            "you have not the permission to add a new corpus group",
            Type.WARNING_MESSAGE);
        }
        else
        {
          log.error("error occures while storing new corpus set", ex);
          Notification.show("error occures while storing new corpus set",
            "Maybe you will have to log in", Type.WARNING_MESSAGE);
        }
      }
    } // end if new item
  }

  @Override
  public Action[] getActions(Object target, Object sender)
  {
    String corpusName = (String) target;
    LinkedList<Action> result = new LinkedList<Action>();

    if (target == null)
    {
      // no action for empty space
      return new Action[0];
    }

    if (Helper.getUser() == null)
    {
      // we can't change anything if we are not logged in so don't even try
      return new Action[0];
    }

    if (userConfig != null)
    {
      for (CorpusSet entry : userConfig.getCorpusSets())
      {
        if (entry.getCorpora().contains(corpusName))
        {
          AddRemoveAction action = new AddRemoveAction(ActionType.Remove, entry,
            corpusName, "Remove from " + entry.getName());
          // add possibility to remove
          result.add(action);
        }
        else
        {
          AddRemoveAction action = new AddRemoveAction(ActionType.Add, entry,
            corpusName, "Add to " + entry.getName());
          // add possibility to add
          result.add(action);
        }
      }
    }

    return result.toArray(new Action[result.size()]);
  }

  @Override
  public void handleAction(Action action, Object sender, Object target)
  {
    if (action instanceof AddRemoveAction && this.userConfig != null)
    {
      AddRemoveAction a = (AddRemoveAction) action;


      int idx = this.userConfig.getCorpusSets().indexOf(a.getCorpusSet());
      if (idx > -1)
      {
        CorpusSet set = this.userConfig.getCorpusSets().get(idx);

        if (a.type == ActionType.Remove)
        {
          set.getCorpora().remove(a.getCorpusId());
          if (set.getCorpora().isEmpty())
          {
            // remove the set itself when it gets empty
            this.userConfig.getCorpusSets().remove(set);

            cbSelection.removeItem(a.getCorpusSet().getName());
            cbSelection.select(ALL_CORPORA);
          }
        }
        else if (a.type == ActionType.Add)
        {
          set.getCorpora().add(a.getCorpusId());
        }

        storeChangesRemote();

        // update view
        updateCorpusTable();
      }
    }
  }
  
  public static class CorpusSorter extends DefaultItemSorter
  {

    @Override
    protected int compareProperty(Object propertyId, boolean sortDirection,
      Item item1, Item item2)
    {
      if ("name".equals(propertyId))
      {
        String val1 = (String) item1.getItemProperty(propertyId).getValue();
        String val2 = (String) item2.getItemProperty(propertyId).getValue();

        if (sortDirection)
        {
          return val1.compareToIgnoreCase(val2);
        }
        else
        {
          return val2.compareToIgnoreCase(val1);
        }
      }
      else
      {
        return super.compareProperty(propertyId, sortDirection, item1, item2);
      }
    }
  }

  public void selectCorpora(Set<String> corpora)
  {
    if (tblCorpora != null)
    {
      tblCorpora.setValue(corpora);
      if (!corpora.isEmpty())
      {
        tblCorpora.setCurrentPageFirstItemId(corpora.iterator().next());
      }
    }
  }

  /**
   * Get the names of the corpora that are currently selected.
   * @return 
   */
  public Set<String> getSelectedCorpora()
  {
    Set<String> result = new HashSet<String>();

    for (String id : corpusContainer.getItemIds())
    {
      if (tblCorpora.isSelected(id))
      {
        result.add(id);
      }
    }

    return result;
  }
  
  /**
   * Get the names of the corpora that are currently visible and can be choosen
   * by the user.
   * @return 
   */
  public Set<String> getVisibleCorpora()
  {
    return new HashSet<String>(corpusContainer.getItemIds());
  }
  
  /**
   * Set the currently displayed corpus set.
   * @param corpusSet 
   */
  public void setCorpusSet(String corpusSet)
  {
    cbSelection.select(corpusSet);
  }

  public class DocLinkGenerator implements Table.ColumnGenerator
  {

    @Override
    public Object generateCell(Table source, Object itemId, Object columnId)
    {
      final String id = (String) itemId;
      
      
      if (ui.getDocBrowserController().docsAvailable(id))
      {
        Button l = new Button();
        l.setStyleName(BaseTheme.BUTTON_LINK);
        l.setIcon(DOC_ICON);

        l.setDescription("opens the document browser for " + id);
        l.addClickListener(new Button.ClickListener()
        {
          @Override
          public void buttonClick(ClickEvent event)
          {
            ui.getDocBrowserController().openDocBrowser(id);
          }
        });
        return l;
      }

      return "";
    }
  }

  public class InfoGenerator implements Table.ColumnGenerator
  {

    @Override
    public Component generateCell(Table source, Object itemId, Object columnId)
    {
      final String id = (String) itemId;
      final Button l = new Button();
      l.setStyleName(BaseTheme.BUTTON_LINK);
      l.setIcon(INFO_ICON);
      l.setDescription("show metadata and annotations for " + id);
      l.addClickListener(new Button.ClickListener()
      {
        @Override
        public void buttonClick(ClickEvent event)
        {
          if (controller != null)
          {
            l.setEnabled(false);
            initCorpusBrowser(id, l);
          }
        }
      });

      return l;
    }
  }

  public static class AddRemoveAction extends Action
  {

    private ActionType type;

    private CorpusSet corpusSet;

    private String corpusId;

    public AddRemoveAction(ActionType type, CorpusSet corpusSet, String corpusId,
      String caption)
    {
      super(caption);
      this.type = type;
      this.corpusSet = corpusSet;
      this.corpusId = corpusId;
    }

    public ActionType getType()
    {
      return type;
    }

    public String getCorpusId()
    {
      return corpusId;
    }

    public CorpusSet getCorpusSet()
    {
      return corpusSet;
    }
  }

  private class CorpusTableChangedListener implements ValueChangeListener
  {

    private final CorpusListPanel finalThis;

    public CorpusTableChangedListener(CorpusListPanel finalThis)
    {
      this.finalThis = finalThis;
    }

    @Override
    public void valueChange(ValueChangeEvent event)
    {
      finalThis.controller.corpusSelectionChangedInBackground();
      updateAutoGeneratedQueriesPanel();
    }
  }

  private static class AnnisCorpusListType extends GenericType<List<AnnisCorpus>>
  {

    public AnnisCorpusListType()
    {
    }
  }

  public Table getTblCorpora()
  {
    return tblCorpora;
  }

  public void initCorpusBrowser(String topLevelCorpusName, final Button l)
  {

    AnnisCorpus c = corpusContainer.getItem(topLevelCorpusName).getBean();
    MetaDataPanel meta = new MetaDataPanel(c.getName());

    CorpusBrowserPanel browse = new CorpusBrowserPanel(c, controller);
    GridLayout infoLayout = new GridLayout(2, 2);
    infoLayout.setSizeFull();
    
    String corpusURL =  Helper.generateCorpusLink(Sets.newHashSet(topLevelCorpusName));
    Label lblLink = new Label("Link to corpus: <a href=\"" + corpusURL + "\">"
      + corpusURL + "</a>", ContentMode.HTML);
    lblLink.setHeight("-1px");
    lblLink.setWidth("-1px");
    
    
    infoLayout.addComponent(meta, 0, 0);
    infoLayout.addComponent(browse, 1, 0);
    infoLayout.addComponent(lblLink, 0,1,1, 1);
    
    infoLayout.setRowExpandRatio(0, 1.0f);
    infoLayout.setColumnExpandRatio(0, 0.5f);
    infoLayout.setColumnExpandRatio(1, 0.5f);
    infoLayout.setComponentAlignment(lblLink, Alignment.MIDDLE_CENTER);
    
    Window window = new Window("Corpus information for " + c.getName()
      + " (ID: " + c.getId() + ")", infoLayout);
    window.setWidth(70, UNITS_EM);
    window.setHeight(45, UNITS_EM);
    window.setResizable(true);
    window.setModal(false);
    window.setResizeLazy(true);

    window.addCloseListener(new Window.CloseListener()
    {

      @Override
      public void windowClose(Window.CloseEvent e)
      {
        l.setEnabled(true);
      }
    });

    UI.getCurrent().addWindow(window);
    window.center();
  }
  
  public static class TooltipGenerator implements AbstractSelect.ItemDescriptionGenerator
  {
    @Override
    public String generateDescription(Component source, Object itemId,
      Object propertyId)
    {
      if("name".equals(propertyId))
      {
        return (String) itemId;
      }
      return null;
    }
    
  }
}
