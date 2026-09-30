extends SceneTree
## Read a real GLB through Godot's importer; never generate replacement meshes.

var meshes: Array[Dictionary] = []
var node_names: Array[String] = []

func _initialize() -> void:
	call_deferred("inspect_file")

func inspect_file() -> void:
	var args := OS.get_cmdline_user_args()
	if args.size() != 1:
		printerr("Usage: Godot --headless --path tools/model-review --script inspect.gd -- /absolute/model.glb")
		quit(2)
		return
	var file_path := args[0]
	if not file_path.is_absolute_path() or not FileAccess.file_exists(file_path):
		printerr("Model must be an existing absolute file path.")
		quit(2)
		return
	var doc := GLTFDocument.new()
	var state := GLTFState.new()
	var result := doc.append_from_file(file_path, state)
	if result != OK:
		printerr("glTF import failed: %s" % error_string(result))
		quit(1)
		return
	var scene := doc.generate_scene(state)
	if scene == null:
		printerr("glTF generated no scene.")
		quit(1)
		return
	root.add_child(scene)
	inspect_node(scene)
	print(JSON.stringify({
		"engine": Engine.get_version_info()["string"],
		"file": file_path,
		"sha256": FileAccess.get_sha256(file_path),
		"bytes": FileAccess.get_file_as_bytes(file_path).size(),
		"nodes": node_names,
		"meshes": meshes,
		"animations": state.animations.size(),
		"quality_status": "Requires visual, license and mechanical structure review; import success is not approval."
	}, "\t"))
	scene.free()
	quit(0 if not meshes.is_empty() else 1)

func inspect_node(node: Node) -> void:
	node_names.append(str(node.get_path()))
	if node is MeshInstance3D and node.mesh != null:
		var mesh_node := node as MeshInstance3D
		var surfaces: Array[Dictionary] = []
		for index in mesh_node.mesh.get_surface_count():
			var arrays: Array = mesh_node.mesh.surface_get_arrays(index)
			var indices = arrays[Mesh.ARRAY_INDEX]
			var vertices = arrays[Mesh.ARRAY_VERTEX]
			var mat: Material = mesh_node.get_active_material(index)
			var item := {
				"vertices": vertices.size() if vertices != null else 0,
				"indices": indices.size() if indices != null else 0,
				"primitive_type": node.mesh.surface_get_primitive_type(index),
				"uv": arrays[Mesh.ARRAY_TEX_UV] != null and not arrays[Mesh.ARRAY_TEX_UV].is_empty(),
				"normals": arrays[Mesh.ARRAY_NORMAL] != null and not arrays[Mesh.ARRAY_NORMAL].is_empty(),
				"material": mat.resource_name if mat != null else "MISSING"
			}
			if mat is BaseMaterial3D:
				item["pbr"] = {
					"metallic": mat.metallic, "roughness": mat.roughness,
					"albedo_texture": mat.albedo_texture != null,
					"normal_texture": mat.normal_texture != null,
					"metallic_texture": mat.metallic_texture != null,
					"roughness_texture": mat.roughness_texture != null,
					"transparency": mat.transparency
				}
			surfaces.append(item)
		var bounds: AABB = node.mesh.get_aabb()
		meshes.append({"node": str(node.get_path()), "local_bounds_metres": str(bounds),
			"transform": str(node.global_transform), "surfaces": surfaces})
	for child in node.get_children():
		inspect_node(child)
