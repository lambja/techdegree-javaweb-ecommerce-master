package com.acme.ecommerce.controller;

import com.acme.ecommerce.domain.Product;
import com.acme.ecommerce.domain.ProductPurchase;
import com.acme.ecommerce.domain.Purchase;
import com.acme.ecommerce.domain.ShoppingCart;
import com.acme.ecommerce.exceptions.ProductNotFoundException;
import com.acme.ecommerce.service.ProductService;
import com.acme.ecommerce.web.FlashMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.math.BigDecimal;

@Controller
@RequestMapping("/product")
@Scope("request")
public class ProductController {
	
	final Logger logger = LoggerFactory.getLogger(ProductController.class);
	
	private static final int INITIAL_PAGE = 0;
	private static final int PAGE_SIZE = 5;

	@Autowired
	ShoppingCart sCart;
	
	@Autowired
	ProductService productService;
	
	@Autowired
	HttpSession session;
	
	@Value("${imagePath:/images/}")
	String imagePath;
	
    @RequestMapping("/")
    public String index(Model model, @RequestParam(value = "page", required = false) Integer page) {
    	logger.debug("Getting Product List");
    	logger.debug("Session ID = " + session.getId());
    	
		// Evaluate page. If requested parameter is null or less than 0 (to
		// prevent exception), return initial size. Otherwise, return value of
		// param. decreased by 1.
		int evalPage = (page == null || page < 1) ? INITIAL_PAGE : page - 1;
    	
    	Page<Product> products = productService.findAll(new PageRequest(evalPage, PAGE_SIZE));

		BigDecimal subTotal = new BigDecimal(0);
		Purchase purchase = sCart.getPurchase();
		if (purchase != null) {
			for (ProductPurchase pp : purchase.getProductPurchases()) {
				logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
				subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
			}
			model.addAttribute("subTotal", subTotal);
		}

		model.addAttribute("products", products);

        return "index";
    }
    
    @RequestMapping(path = "/detail/{id}", method = RequestMethod.GET)
    public String productDetail(@PathVariable long id, Model model) {
    	logger.debug("Details for Product " + id);
    	
    	Product returnProduct = productService.findById(id);
		model.addAttribute("product", returnProduct);
		ProductPurchase productPurchase = new ProductPurchase();
		productPurchase.setProduct(returnProduct);
		productPurchase.setQuantity(1);
		model.addAttribute("productPurchase", productPurchase);

		BigDecimal subTotal = new BigDecimal(0);
		Purchase purchase = sCart.getPurchase();
		if (purchase != null) {
			for (ProductPurchase pp : purchase.getProductPurchases()) {
				logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
				subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
			}
			model.addAttribute("subTotal", subTotal);
		}

        return "product_detail";
    }
    
    @RequestMapping(path="/{id}/image", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<InputStreamResource> productImage(@PathVariable long id) throws FileNotFoundException {
    	MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    	
    	logger.debug("Product Image Request for " + id);
    	logger.info("Using imagePath [" + imagePath + "]");
    	
    	Product returnProduct = productService.findById(id);
    	String imageFilePath = null;
    	if (returnProduct != null) {
    		if (!imagePath.endsWith("/")) {
    			imagePath = imagePath + "/";
    		}
    		imageFilePath = imagePath + returnProduct.getFullImageName();
    	} 
    	File imageFile = new File(imageFilePath);
    	
    	return ResponseEntity.ok()
                .contentLength(imageFile.length())
                .contentType(MediaType.parseMediaType(mimeTypesMap.getContentType(imageFile)))
                .body(new InputStreamResource(new FileInputStream(imageFile)));
    }
    
    @RequestMapping("/about")
    public String aboutCartShop(Model model) {
    	logger.warn("Happy Easter! Someone actually clicked on About.");

		BigDecimal subTotal = new BigDecimal(0);
		Purchase purchase = sCart.getPurchase();
		if (purchase != null) {
			for (ProductPurchase pp : purchase.getProductPurchases()) {
				logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
				subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
			}
			model.addAttribute("subTotal", subTotal);
		}

    	return "about";
    }

	@ResponseStatus(value= HttpStatus.NOT_FOUND	)
	@ExceptionHandler({ProductNotFoundException.class})
	public String productNotFoundError(Exception exception, Model model){

		logger.error("Product id not found!");

		model.addAttribute("exception", exception.getMessage());

		return "/error";
	}
}
