package edu.stanford.nlp.sempre.interactive.actions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.Json;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph;
import edu.stanford.nlp.sempre.StringValue;

enum CubeColor {
  Red(0), Orange(1), Yellow (2), Green(3), Blue(4), White(6), Black(7),
  Pink(8), Brown(9), Gray(10), Fake(11), None(-5);
  private final int value;
  private static final int MAXCOLOR = 7;
  CubeColor(int value) { this.value = value; }
  public int toInt() { return this.value; }
  public boolean Compare(int i){return value == i;}
  public static CubeColor fromInt(int intc) {
    for(CubeColor c : CubeColor.values())
    {
      if (intc < 0) return CubeColor.None;
      if (c.value == intc % (CubeColor.values().length-1)) return c;
    }
    return CubeColor.None;
  }
  public static CubeColor fromString(String color) {
    for(CubeColor c : CubeColor.values())
      if (c.name().equalsIgnoreCase(color)) return c;
    return CubeColor.None;
  }
}
enum Direction {
  Top, Bot, Left, Right, Front, Back, None;
  public static Direction fromString(String dir) {
    dir = dir.toLowerCase();
    if (dir.equals("up") || dir.equals("top"))
      return Direction.Top;
    if (dir.equals("down") || dir.equals("bot"))
      return Direction.Bot;
    if (dir.equals("left"))
      return Direction.Left;
    if (dir.equals("right"))
      return Direction.Right;
    if (dir.equals("front"))
      return Direction.Front;
    if (dir.equals("back"))
      return Direction.Back;
    return Direction.None;
  }
}

// the world of stacks
public class BlocksWorld extends FlatWorld {
  public final static String SELECT = "S";

  public static BlocksWorld fromContext(ContextValue context) {
    if (context == null || context.graph == null) {
      return fromJSON("[[3,3,1,\"Gray\",[\"S\"]],[4,4,1,\"Blue\",[]]]");
    }
    NaiveKnowledgeGraph graph = (NaiveKnowledgeGraph)context.graph;
    String wallString = ((StringValue)graph.triples.get(0).e1).value;
    return fromJSON(wallString);
  }

  public void base(int x, int y) {
    Block basecube = new Block(x, y, 0, CubeColor.Fake.toString());
    this.allitems.add(basecube);
    this.selected.add(basecube);
  }

  @SuppressWarnings("unchecked")
  public BlocksWorld(Set<Item> blockset) {
    super();
    this.allitems = blockset;
    this.selected = blockset.stream().filter(b -> ((Block)b).names.contains(SELECT)).collect(Collectors.toSet());
  }

  public String toJSON() {
    // selected thats no longer in the world gets nothing
    Set<Item> outset = new HashSet<>(selected);
    for (Item i : allitems) {
      i.names.remove(SELECT);
    }
    for (Item i : outset) {
      i.names.add(SELECT);
    }
    outset.addAll(allitems);
    
    return Json.writeValueAsStringHard(outset.stream()
        .map(c -> ((Block)c).toJSON()).collect(Collectors.toList()));
    // return this.worldlist.stream().map(c -> c.toJSON()).reduce("", (o, n) -> o+","+n);
  }

  private static BlocksWorld fromJSON(String wallString) {
    @SuppressWarnings("unchecked")
    List<List<Object>> cubestr = Json.readValueHard(wallString, List.class);
    Set<Item> cubes = cubestr.stream().map(c -> {return Block.fromJSONObject(c);})
        .collect(Collectors.toSet());
    // throw new RuntimeException(a.toString()+a.get(1).toString());
    BlocksWorld world = new BlocksWorld(cubes);
    world.selected.addAll(cubes.stream().filter(b -> ((Block)b).names.contains(SELECT)).collect(Collectors.toSet()));
    // world.previous.addAll(world.selected);
    // we can only use previous within a block;
    return world;
  }

  @Override
  public Set<Item> has(String rel, Set<Object> values) {
    // LogInfo.log(values);
    return this.allitems.stream().filter(i -> values.contains(i.get(rel)))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<Object> get(String rel, Set<Item> subset) {
    return subset.stream().map(i -> i.get(rel))
        .collect(Collectors.toSet());
  }

  @Override
  public void update(String rel, Object value, Set<Item> selected) {
    allitems.removeAll(selected);
    selected.forEach(i -> i.update(rel, value));
    allitems.addAll(selected);
  }


  // block world specific actions, non-overriding move
  public void move(String dir, Set<Item> selected) {
    allitems.removeAll(selected);
    realBlocks(selected).forEach(b -> ((Block)b).move(Direction.fromString(dir)));
    allitems.addAll(selected); // this is not overriding
    // allitems = selected.allAll(allitems); // overriding move
  }
  
  public void add(String colorstr, String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    CubeColor color = CubeColor.fromString(colorstr);
    
    if (dir == Direction.None) { // add here
      selected.forEach(b -> ((Block)b).color = color);
    } else {
      Set<Item> extremeCubes = extremeCubes(dir, selected);
      this.allitems.addAll(extremeCubes.stream().map(
          c -> {
            Block d = ((Block)c).copy(dir);
            d.color = color;
            return d;}
          )
          .collect(Collectors.toList()));
    }
  }
  
  // get cubes at the outer locations
  private Set<Item> extremeCubes(Direction dir, Set<Item> selected) {
    Set<Item> realCubes = realBlocks(allitems);
    return selected.stream().map(c -> {
      Block d = (Block)c;
      while(realCubes.contains(d.copy(dir)))
        d = d.copy(dir);
      return d;
    }).collect(Collectors.toSet());
  }
  
  public Set<Item> allBlocks() {
    return realBlocks(this.all());
  }
  
  private Set<Item> realBlocks(Set<Item> all) {
    return all.stream().filter(b-> ((Block)b).color != CubeColor.Fake)
        .collect(Collectors.toSet());
  }

  //get cubes at extreme positions
  public Set<Item> veryx(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    switch (dir) {
    case Back: return argmax(c -> c.row, selected);
    case Front: return argmax(c -> -c.row, selected);
    case Left: return argmax(c -> c.col, selected);
    case Right: return argmax(c -> -c.col, selected);
    case Top: return argmax(c -> c.height, selected);
    case Bot: return argmax(c -> -c.height, selected);
    default: throw new RuntimeException("invalid direction");
    }
  }

  // return retrieved from allitems, along with any potential selectors which are empty.
  public Set<Item> adj(String dirstr, Set<Item> selected) {
    Direction dir = Direction.fromString(dirstr);
    Set<Item> selectors = selected.stream()
        .map(c -> {Item d = ((Block)c).copy(dir); ((Block)d).color = CubeColor.Fake; return d;})
        .collect(Collectors.toSet());

    this.allitems.addAll(selectors);
    
    Set<Item> actual = allitems.stream().filter(c -> selectors.contains(c))
        .collect(Collectors.toSet());
    
    return actual;
  }

  public static Set<Item> argmax(Function<Block, Integer> f, Set<Item> items) {
    int maxvalue = Integer.MIN_VALUE;
    for (Item i : items) {
      int cvalue = f.apply((Block)i);
      if (cvalue > maxvalue) maxvalue = cvalue;
    }
    final int maxValue = maxvalue;
    return items.stream().filter(c -> f.apply((Block)c) >= maxValue).collect(Collectors.toSet());
  }
  
  // deprecated
  public void build(String cubejson, Set<Item> selected) {
    for (Item i : selected) {
      BlocksWorld world = BlocksWorld.fromJSON(cubejson);
      Block b = ((Block)i);
      // shifts world to position b
      shift(b, world);
      
      this.allitems.remove(i);
      this.allitems.addAll(world.allitems);
      if (!this.allitems.contains(i)) this.allitems.add(i);
    }
  }

  // make block the anchor of the world
  private void shift(Block block, BlocksWorld world) {
    Block anchor = new Block(0,0,0,"None");
    for (Item i  : world.allitems) {
      Block b = ((Block)i);
      b.row = b.row - anchor.row + block.row;
      b.col = b.col - anchor.col + block.col;
      b.height = b.height - anchor.height + block.height;
    }
  }
}
