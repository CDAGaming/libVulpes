package zmaster587.libVulpes.util;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.recipe.NumberedOreDictStack;
import zmaster587.libVulpes.recipe.RecipesMachine;
import zmaster587.libVulpes.tile.TileEntityMachine;

public class XMLRecipeLoader {

	Document doc;
	String fileName;

	public XMLRecipeLoader() {
		doc = null;
		fileName = "";
	}

	public boolean loadFile(File xmlFile) throws IOException {
		DocumentBuilder docBuilder;
		doc = null;
		try {
			docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			return false;
		}

		try {
			doc = docBuilder.parse(xmlFile);
		} catch (SAXException e) {
			e.printStackTrace();
			return false;
		}

		fileName = xmlFile.getAbsolutePath();

		return true;
	}

	public void registerRecipes(Class<? extends TileEntityMachine> clazz) {
		Node masterNode = doc.getElementsByTagName("Recipes").item(0);
		int recipeNum = 1;

		if(masterNode.hasAttributes()) {
			Node defaultNode = masterNode.getAttributes().getNamedItem("useDefault");
			if(defaultNode != null && defaultNode.getNodeValue().equals("false"))
				RecipesMachine.getInstance().clearRecipes(clazz);
		}

		masterNode = masterNode.getChildNodes().item(1);

		while(masterNode != null) {
			int time = 200, energy = 0;
			if(masterNode.getNodeType() != doc.ELEMENT_NODE) {
				masterNode = masterNode.getNextSibling();
				continue;
			}
			if(!masterNode.getNodeName().equals("Recipe")) {
				LibVulpes.logger.warning("Expected \"Recipe\" Node in " + fileName + ", found " + masterNode.getNodeName() + "!  Skipping.");
				masterNode = masterNode.getNextSibling();
				continue;
			}

			Node inputNode = null, outputNode = null;
			for(int i = 0; i < masterNode.getChildNodes().getLength(); i++) {
				Node node = masterNode.getChildNodes().item(i);
				if(node.getNodeName().equals("input")) {
					inputNode = node;
				}
				else if(node.getNodeName().equals("output")) {
					outputNode = node;
				}
			}

			if(outputNode == null) {
				masterNode = masterNode.getNextSibling();
				LibVulpes.logger.warning("Missing \"output\" Node in recipe " + recipeNum + " in " + fileName + "!  Skipping.");
				recipeNum++;
				continue;
			}
			if(inputNode == null) {
				masterNode = masterNode.getNextSibling();
				LibVulpes.logger.warning("Missing \"input\" Node in recipe " + recipeNum + " in " + fileName + "!  Skipping.");
				recipeNum++;
				continue;
			}

			List<Object> inputList = new LinkedList<Object>();

			for(int i = 1; i < inputNode.getChildNodes().getLength(); i++) {
				Node node = inputNode.getChildNodes().item(i);
				if(node.getNodeType() != doc.ELEMENT_NODE) continue;

				Object obj = parseItemType(node, false);
				if(obj == null) {
					LibVulpes.logger.warning("Invalid item \"input\" (" + node.getNodeName() + " " + node.getTextContent() + ") in recipe " + recipeNum + " in " + fileName + "!  Skipping.");
				}
				else
					inputList.add(obj);
			}

			List<Object> outputList = new LinkedList<Object>();

			for(int i = 1; i < outputNode.getChildNodes().getLength(); i++) {
				Node node = outputNode.getChildNodes().item(i);

				if(node.getNodeType() != doc.ELEMENT_NODE) continue;

				Object obj = parseItemType(node, true);
				if(obj == null) {
					LibVulpes.logger.warning("Invalid item \"output\" (" + node.getNodeName() + " " + node.getTextContent() + ") in recipe " + recipeNum + " in " + fileName + "!  Skipping.");
				}
				else
					outputList.add(obj);
			}

			if(masterNode.hasAttributes()) {
				Node node = masterNode.getAttributes().getNamedItem("timeRequired");
				if(node != null && !node.getNodeValue().isEmpty()) {
					try {
						time = Integer.parseInt(node.getNodeValue());
					} catch (NumberFormatException e) {
						LibVulpes.logger.warning("Recipe " + recipeNum + " has no time value");
					}
				}

				node = masterNode.getAttributes().getNamedItem("power");
				if(node != null && !node.getNodeValue().isEmpty()) {
					try {
						energy = Integer.parseInt(node.getNodeValue());
					} catch (NumberFormatException e) {
						LibVulpes.logger.warning("Recipe " + recipeNum + " has no power value");
					}
				}
			}
			else {
				LibVulpes.logger.info("Recipe " + recipeNum + " has no time or power consumption");
			}

			if(outputList.isEmpty()) 
				LibVulpes.logger.info("Output List emtpy in recipe " + recipeNum);
			else {
				RecipesMachine.getInstance().addRecipe(clazz, outputList, time, energy, inputList);
				LibVulpes.logger.info("Sucessfully added recipe to " + clazz.getName() + " for " + inputList.toString() + " -> " + outputList.toString());
			}
			
			masterNode = masterNode.getNextSibling();
		}
	}

	public Object parseItemType(Node node, boolean output) {
		if(node.getNodeName().equals("itemStack")) {
			String text = node.getTextContent();
			String splitStr[] = text.split(" ");
			int meta = 0;
			int size = 1;
			//format: "name meta size"
			if(splitStr.length > 1) {
				try {
					size = Integer.parseInt(splitStr[1]);
				} catch( NumberFormatException e) {}
			}
			if(splitStr.length > 2) {
				try {
					meta= Integer.parseInt(splitStr[2]);
				} catch (NumberFormatException e) {}
			}

			ItemStack stack = null;
			Block block = Block.getBlockFromName(splitStr[0]);
			if(block == null) {

				//Try getting item by name first
				Item item = (Item) Item.itemRegistry.getObject(splitStr[0]);

				if(item != null)
					stack = new ItemStack(item, size, meta);
				else {
					try {

						item = Item.getItemById(Integer.parseInt(splitStr[0]));
						if(item != null)
							stack = new ItemStack(item, size, meta);
					} catch (NumberFormatException e) { return null;}

				}
			}
			else
				stack = new ItemStack(block, size, meta);

			return stack;
		}
		else if(node.getNodeName().equals("oreDict")) {
			String splitStr[] = node.getTextContent().split(" ");
			if(OreDictionary.doesOreNameExist(splitStr[0])) {

				Object ret = splitStr[0];
				int number = 1;
				if(splitStr.length > 1) {

					try {
						number = Integer.parseInt(splitStr[1]);
					} catch (NumberFormatException e) {}
				}

				if(splitStr.length >= 1) {
					if(output) {
						List<ItemStack> list = OreDictionary.getOres(splitStr[0]);
						if(!list.isEmpty()) {
							ItemStack oreDict = OreDictionary.getOres(splitStr[0]).get(0);
							ret = new ItemStack(oreDict.getItem(), number, oreDict.getItemDamage());
						}
					}
					else
						ret = new NumberedOreDictStack(splitStr[0], number);
				}

				return ret;
			}
		}
		else if(node.getNodeName().equals("fluidStack")) {

			String splitStr[] = node.getTextContent().split(" ");
			Fluid fluid;
			if((fluid = FluidRegistry.getFluid(splitStr[0])) != null) {
				int amount = 1000;
				if(splitStr.length > 1) {
					try {
						amount = Integer.parseInt(splitStr[1]);
					} catch (NumberFormatException e) {}
				}

				return new FluidStack(fluid, amount);
			}
		}

		return null;
	}
}
