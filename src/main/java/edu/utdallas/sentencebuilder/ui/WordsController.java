package edu.utdallas.sentencebuilder.ui;

import edu.utdallas.sentencebuilder.AppContext;
import edu.utdallas.sentencebuilder.model.FollowerRow;
import edu.utdallas.sentencebuilder.model.WordRow;
import edu.utdallas.sentencebuilder.repo.WordRepository;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;

import java.util.List;
import java.util.function.BiFunction;

/** Browse, filter, sort and edit the word table and its bigrams. */
public final class WordsController implements Refreshable {

    @FXML private TextField filter;
    @FXML private Spinner<Integer> minCount;
    @FXML private ComboBox<WordRepository.Sort> sort;
    @FXML private Spinner<Integer> limit;
    @FXML private Label countLabel;
    @FXML private TableView<WordRow> table;
    @FXML private TableColumn<WordRow, String> colText;
    @FXML private TableColumn<WordRow, Integer> colTotal;
    @FXML private TableColumn<WordRow, Integer> colStart;
    @FXML private TableColumn<WordRow, Integer> colEnd;
    @FXML private TableColumn<WordRow, Integer> colFollowers;

    @FXML private Label followersLabel;
    @FXML private TableView<FollowerRow> followers;
    @FXML private TableColumn<FollowerRow, String> colFNext;
    @FXML private TableColumn<FollowerRow, Integer> colFCount;
    @FXML private TableColumn<FollowerRow, Integer> colFChosen;

    @FXML private Label predecessorsLabel;
    @FXML private TableView<FollowerRow> predecessors;
    @FXML private TableColumn<FollowerRow, String> colPPrev;
    @FXML private TableColumn<FollowerRow, Integer> colPCount;

    @FXML
    private void initialize() {
        Spinners.integer(minCount, 0, 1_000_000, 1);
        Spinners.integer(limit, 10, 200_000, 5_000);
        sort.getItems().setAll(WordRepository.Sort.values());
        sort.getSelectionModel().selectFirst();

        colText.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().text()));
        colFollowers.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().followerCount()));
        editableCount(colTotal, WordRow::total, (row, v) -> row.withCounts(v, row.start(), row.end()));
        editableCount(colStart, WordRow::start, (row, v) -> row.withCounts(row.total(), v, row.end()));
        editableCount(colEnd, WordRow::end, (row, v) -> row.withCounts(row.total(), row.start(), v));

        table.getSelectionModel().selectedItemProperty().addListener((o, a, row) -> loadNeighbours(row));
        table.setPlaceholder(new Label("No words match."));

        colFNext.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().nextWord()));
        colFChosen.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().chosenCount()));
        colFCount.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().count()));
        colFCount.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colFCount.setOnEditCommit(e -> {
            FollowerRow row = e.getRowValue();
            Integer v = e.getNewValue();
            if (v == null || v < 0) {
                followers.refresh();
                return;
            }
            Async.run(() -> {
                AppContext.get().words.updateFollowCount(row.wordId(), row.nextWordId(), v);
                return row.withCount(v);
            }, updated -> followers.getItems().set(e.getTablePosition().getRow(), updated));
        });
        followers.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && followers.getSelectionModel().getSelectedItem() != null) {
                jumpTo(followers.getSelectionModel().getSelectedItem().nextWord());
            }
        });

        colPPrev.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().nextWord()));
        colPCount.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().count()));
        predecessors.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && predecessors.getSelectionModel().getSelectedItem() != null) {
                jumpTo(predecessors.getSelectionModel().getSelectedItem().nextWord());
            }
        });
    }

    /** Wires an editable integer column that writes straight through to the database. */
    private void editableCount(TableColumn<WordRow, Integer> col,
                               java.util.function.Function<WordRow, Integer> getter,
                               BiFunction<WordRow, Integer, WordRow> setter) {
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(getter.apply(cd.getValue())));
        col.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        col.setOnEditCommit(e -> {
            WordRow row = e.getRowValue();
            Integer v = e.getNewValue();
            if (v == null || v < 0) {
                table.refresh();
                return;
            }
            WordRow updated = setter.apply(row, v);
            Async.run(() -> {
                AppContext.get().words.updateCounts(updated.id(), updated.total(), updated.start(), updated.end());
                return updated;
            }, u -> table.getItems().set(e.getTablePosition().getRow(), u));
        });
    }

    @FXML
    @Override
    public void refresh() {
        String f = filter.getText();
        int min = Spinners.value(minCount);
        WordRepository.Sort s = sort.getValue();
        int lim = Spinners.value(limit);
        countLabel.setText("Loading…");
        Async.run(() -> AppContext.get().words.search(f, min, s, lim), rows -> {
            table.getItems().setAll(rows);
            countLabel.setText(rows.size() + (rows.size() >= lim ? "+ words (limit reached)" : " words"));
        });
    }

    private void loadNeighbours(WordRow row) {
        if (row == null) {
            followers.getItems().clear();
            predecessors.getItems().clear();
            followersLabel.setText("Followed by");
            predecessorsLabel.setText("Comes after");
            return;
        }
        followersLabel.setText("\"" + row.text() + "\" is followed by");
        predecessorsLabel.setText("\"" + row.text() + "\" comes after");
        Async.run(() -> AppContext.get().words.followers(row.id()), followers.getItems()::setAll);
        Async.run(() -> AppContext.get().words.predecessors(row.id()), predecessors.getItems()::setAll);
    }

    private void jumpTo(String word) {
        filter.setText(word);
        Async.run(() -> AppContext.get().words.find(word), found -> {
            List<WordRow> rows = found.map(List::of).orElse(List.of());
            table.getItems().setAll(rows);
            countLabel.setText(rows.size() + " words");
            if (!rows.isEmpty()) {
                table.getSelectionModel().selectFirst();
            }
        });
    }
}
